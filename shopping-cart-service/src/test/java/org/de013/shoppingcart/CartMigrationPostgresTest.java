package org.de013.shoppingcart;

import org.de013.shoppingcart.entity.Cart;
import org.de013.shoppingcart.entity.CartItem;
import org.de013.shoppingcart.config.CouponMigrationGuard;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class CartMigrationPostgresTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    private Flyway flyway(String schema, String target) {
        var config = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(schema).defaultSchema(schema).callbacks(new CouponMigrationGuard());
        if (target != null) config.target(target);
        return config.load();
    }

    private Connection connect(String schema) throws Exception {
        Connection connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        connection.setSchema(schema);
        return connection;
    }

    private String fingerprint(String schema) throws Exception {
        String script;
        try (var input = getClass().getResourceAsStream("/db/cart-preflight.sql")) {
            script = new String(input.readAllBytes(), StandardCharsets.UTF_8).replace("\\set ON_ERROR_STOP on", "");
        }
        int end = script.indexOf("END $$;") + "END $$;".length();
        try (Connection c = connect(schema); Statement s = c.createStatement()) {
            s.execute(script.substring(0, end));
            try (ResultSet r = s.executeQuery(script.substring(end))) {
                assertTrue(r.next()); return r.getString(1);
            }
        }
    }

    @Test
    void preflightDetectsUnvalidatedForeignKeyAndInvalidIndex() throws Exception {
        flyway("reference_flags", "3").migrate();
        flyway("legacy_flags", "3").migrate();
        try (Connection c = connect("legacy_flags"); Statement s = c.createStatement()) {
            s.execute("alter table cart_items drop constraint fk_cart_item_cart");
            s.execute("alter table cart_items add constraint fk_cart_item_cart foreign key (cart_id) references carts(id) on delete cascade not valid");
            assertNotEquals(fingerprint("reference_flags"), fingerprint("legacy_flags"));
            s.execute("alter table cart_items validate constraint fk_cart_item_cart");
            assertEquals(fingerprint("reference_flags"), fingerprint("legacy_flags"));
            s.execute("create table index_fixture (value integer)");
            s.execute("insert into index_fixture values (1), (1)");
            assertThrows(java.sql.SQLException.class, () -> s.execute("create unique index concurrently fixture_idx on index_fixture(value)"));
            try (ResultSet r = s.executeQuery("select indisvalid from pg_index where indexrelid='fixture_idx'::regclass")) {
                assertTrue(r.next()); assertFalse(r.getBoolean(1));
            }
            try (Connection reference = connect("reference_flags"); Statement rs = reference.createStatement()) {
                rs.execute("create table index_fixture (value integer)");
                rs.execute("create unique index fixture_idx on index_fixture(value)");
            }
            assertNotEquals(fingerprint("reference_flags"), fingerprint("legacy_flags"));
        }
    }

    @Test
    void nativeFlywayValidationDetectsChangedHistoryChecksum() throws Exception {
        Flyway reference = flyway("reference_checksum", "3");
        reference.migrate();
        reference.validate();
        try (Connection c = connect("reference_checksum"); Statement s = c.createStatement()) {
            s.execute("update flyway_schema_history set checksum=checksum+1 where version='1'");
        }
        assertThrows(FlywayException.class, reference::validate);
    }

    @Test
    void preflightChecksLegacyShapesDriftAndCoupon() throws Exception {
        flyway("reference_v1", "1").migrate();
        flyway("legacy_v1", "1").migrate();
        try (Connection c = connect("legacy_v1"); Statement s = c.createStatement()) {
            s.execute("drop table flyway_schema_history");
        }
        assertEquals(fingerprint("reference_v1"), fingerprint("legacy_v1"));
        try (Connection c = connect("legacy_v1"); Statement s = c.createStatement()) {
            s.execute("insert into carts(coupon_code) values ('keep')");
            assertThrows(java.sql.SQLException.class, () -> fingerprint("legacy_v1"));
            try (ResultSet r = s.executeQuery("select coupon_code from carts")) {
                assertTrue(r.next()); assertEquals("keep", r.getString(1));
            }
            s.execute("update carts set coupon_code=null");
            s.execute("alter table carts alter column status drop default");
        }
        assertNotEquals(fingerprint("reference_v1"), fingerprint("legacy_v1"));

        flyway("reference_v3", "3").migrate();
        flyway("legacy_v3", "3").migrate();
        try (Connection c = connect("legacy_v3"); Statement s = c.createStatement()) {
            s.execute("drop table flyway_schema_history");
        }
        assertEquals(fingerprint("reference_v3"), fingerprint("legacy_v3"));
        try (Connection c = connect("legacy_v3"); Statement s = c.createStatement()) {
            s.execute("drop index idx_cart_status");
        }
        assertNotEquals(fingerprint("reference_v3"), fingerprint("legacy_v3"));
    }

    @Test
    void preflightRejectsForeignKeyAndSequenceDriftEvenWithBaselineHistory() throws Exception {
        flyway("reference_constraints", "3").migrate();
        flyway("legacy_constraints", "3").migrate();
        try (Connection c = connect("legacy_constraints"); Statement s = c.createStatement()) {
            s.execute("delete from flyway_schema_history where version is not null");
            s.execute("insert into flyway_schema_history(installed_rank,version,description,type,script,installed_by,execution_time,success) values (1,'3','baseline','BASELINE','baseline',current_user,0,true)");
            s.execute("alter table cart_items drop constraint fk_cart_item_cart");
            s.execute("alter table cart_items add constraint fk_cart_item_cart foreign key (cart_id) references carts(id)");
        }
        assertNotEquals(fingerprint("reference_constraints"), fingerprint("legacy_constraints"));
        try (Connection c = connect("legacy_constraints"); Statement s = c.createStatement()) {
            s.execute("alter table cart_items drop constraint fk_cart_item_cart");
            s.execute("alter table cart_items add constraint fk_cart_item_cart foreign key (cart_id) references carts(id) on delete cascade");
            s.execute("alter sequence carts_id_seq owned by none");
        }
        assertNotEquals(fingerprint("reference_constraints"), fingerprint("legacy_constraints"));
    }

    private void history(Connection c) throws Exception {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(
                "select count(*), max(version::int), bool_and(success) from flyway_schema_history where version is not null")) {
            assertTrue(r.next());
            assertEquals(3, r.getInt(1));
            assertEquals(3, r.getInt(2));
            assertTrue(r.getBoolean(3));
        }
    }

    private StandardServiceRegistry registry(String schema) {
        return new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.driver_class", "org.postgresql.Driver")
                .applySetting("hibernate.connection.url", postgres.getJdbcUrl())
                .applySetting("hibernate.connection.username", postgres.getUsername())
                .applySetting("hibernate.connection.password", postgres.getPassword())
                .applySetting("hibernate.default_schema", schema)
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.hbm2ddl.auto", "validate").build();
    }

    private SessionFactory sessionFactory(StandardServiceRegistry registry) {
        return new MetadataSources(registry).addAnnotatedClass(Cart.class)
                .addAnnotatedClass(CartItem.class).buildMetadata().buildSessionFactory();
    }

    @Test
    void emptyDatabaseMigratesAndValidatesJpaCrud() throws Exception {
        flyway("empty_cart", null).migrate();
        try (Connection c = connect("empty_cart"); Statement s = c.createStatement()) {
            history(c);
            try (ResultSet r = s.executeQuery("select table_name, column_name, numeric_precision, numeric_scale, is_nullable, character_maximum_length from information_schema.columns where table_schema=current_schema() and ((table_name='cart_items' and column_name in ('unit_price','weight','currency','product_brand')) or (table_name='carts' and column_name='currency')) order by table_name,column_name")) {
                assertTrue(r.next()); assertEquals("currency", r.getString(2)); assertEquals("NO", r.getString(5)); assertEquals(3, r.getInt(6));
                assertTrue(r.next()); assertEquals("product_brand", r.getString(2)); assertEquals("YES", r.getString(5)); assertEquals(100, r.getInt(6));
                assertTrue(r.next()); assertEquals("unit_price", r.getString(2)); assertEquals(19, r.getInt(3)); assertEquals(2, r.getInt(4));
                assertTrue(r.next()); assertEquals("weight", r.getString(2)); assertEquals(10, r.getInt(3)); assertEquals(3, r.getInt(4));
                assertTrue(r.next()); assertEquals("carts", r.getString(1)); assertEquals("NO", r.getString(5)); assertEquals(3, r.getInt(6)); assertFalse(r.next());
            }
            try (ResultSet r = s.executeQuery("select confdeltype from pg_constraint where conname='fk_cart_item_cart' and connamespace=current_schema()::regnamespace")) {
                assertTrue(r.next()); assertEquals("c", r.getString(1));
            }
        }
        StandardServiceRegistry registry = registry("empty_cart");
        try (SessionFactory factory = sessionFactory(registry)) {
            Long id;
            try (var session = factory.openSession()) {
                var tx = session.beginTransaction();
                Cart cart = Cart.builder().userId("user-1").currency("EUR").build();
                CartItem item = CartItem.builder().productId("product-1").productName("Item").productBrand("Brand")
                        .quantity(2).unitPrice(new BigDecimal("12.34")).totalPrice(new BigDecimal("24.68"))
                        .currency("EUR").addedAt(LocalDateTime.now()).build();
                cart.addItem(item); session.persist(cart); session.flush(); id = cart.getId(); session.clear();
                Cart loaded = session.find(Cart.class, id);
                assertEquals("user-1", loaded.getUserId()); assertEquals(new BigDecimal("24.68"), loaded.getSubtotal());
                assertEquals("Brand", loaded.getCartItems().get(0).getProductBrand());
                assertEquals("EUR", loaded.getCartItems().get(0).getCurrency());
                loaded.setNotes("updated"); session.flush(); session.clear();
                assertEquals("updated", session.find(Cart.class, id).getNotes()); tx.commit();
            }
            try (Connection c = connect("empty_cart"); Statement s = c.createStatement()) {
                c.setAutoCommit(false); s.executeUpdate("delete from carts where id=" + id);
                try (ResultSet r = s.executeQuery("select count(*) from cart_items where cart_id=" + id)) { assertTrue(r.next()); assertEquals(0, r.getInt(1)); }
                c.rollback();
                try (ResultSet r = s.executeQuery("select count(*) from cart_items where cart_id=" + id)) { assertTrue(r.next()); assertEquals(1, r.getInt(1)); }
            }
        } finally { StandardServiceRegistryBuilder.destroy(registry); }
    }

    @Test
    void v1SeedSurvivesUpgradeAfterCouponGuard() throws Exception {
        flyway("upgrade_cart", "1").migrate();
        try (Connection c = connect("upgrade_cart"); Statement s = c.createStatement()) {
            s.executeUpdate("insert into carts(id,user_id,currency,subtotal,total_amount,item_count,total_quantity,notes,coupon_code) values (101,'legacy-user','EUR',24.68,24.68,1,2,'keep note','OLD')");
            s.executeUpdate("insert into cart_items(id,cart_id,product_id,product_name,quantity,unit_price,total_price,currency,product_sku,weight) values (201,101,'legacy-product','Legacy',2,12.34,24.68,'EUR','SKU',1.234)");
        }
        assertThrows(FlywayException.class, () -> flyway("upgrade_cart", null).migrate());
        try (Connection c = connect("upgrade_cart"); Statement s = c.createStatement()) {
            try (ResultSet r = s.executeQuery("select coupon_code from carts where id=101")) {
                assertTrue(r.next()); assertEquals("OLD", r.getString(1));
            }
            try (ResultSet r = s.executeQuery("select count(*) from flyway_schema_history where version is not null")) {
                assertTrue(r.next()); assertEquals(1, r.getInt(1));
            }
            s.executeUpdate("update carts set coupon_code=null where id=101");
        }
        flyway("upgrade_cart", null).migrate();
        try (Connection c = connect("upgrade_cart"); Statement s = c.createStatement()) {
            history(c);
            try (ResultSet r = s.executeQuery("select c.id,c.user_id,c.currency,c.subtotal,c.total_amount,c.item_count,c.total_quantity,c.notes,i.id,i.cart_id,i.product_id,i.product_name,i.quantity,i.unit_price,i.total_price,i.currency,i.product_sku,i.weight,i.product_brand from carts c join cart_items i on i.cart_id=c.id")) {
                assertTrue(r.next()); assertEquals(101, r.getLong(1)); assertEquals("legacy-user", r.getString(2)); assertEquals("EUR", r.getString(3));
                assertEquals(new BigDecimal("24.68"), r.getBigDecimal(4)); assertEquals(new BigDecimal("24.68"), r.getBigDecimal(5));
                assertEquals(1, r.getInt(6)); assertEquals(2, r.getInt(7)); assertEquals("keep note", r.getString(8));
                assertEquals(201, r.getLong(9)); assertEquals(101, r.getLong(10)); assertEquals("legacy-product", r.getString(11)); assertEquals("Legacy", r.getString(12));
                assertEquals(2, r.getInt(13)); assertEquals(new BigDecimal("12.34"), r.getBigDecimal(14)); assertEquals(new BigDecimal("24.68"), r.getBigDecimal(15));
                assertEquals("EUR", r.getString(16)); assertEquals("SKU", r.getString(17)); assertEquals(new BigDecimal("1.234"), r.getBigDecimal(18)); assertNull(r.getString(19)); assertFalse(r.next());
            }
            try (ResultSet r = s.executeQuery("select count(*) from information_schema.columns where table_schema=current_schema() and table_name='carts' and column_name='coupon_code'")) { assertTrue(r.next()); assertEquals(0, r.getInt(1)); }
        }
        StandardServiceRegistry registry = registry("upgrade_cart");
        try (SessionFactory factory = sessionFactory(registry); var session = factory.openSession()) {
            var tx = session.beginTransaction();
            Cart cart = session.find(Cart.class, 101L);
            assertNotNull(cart);
            session.flush(); session.clear();
            cart = session.find(Cart.class, 101L);
            assertEquals("legacy-user", cart.getUserId()); assertEquals("EUR", cart.getCurrency());
            assertEquals(new BigDecimal("24.68"), cart.getSubtotal()); assertEquals(new BigDecimal("24.68"), cart.getTotalAmount());
            assertEquals(1, cart.getItemCount()); assertEquals(2, cart.getTotalQuantity()); assertEquals("keep note", cart.getNotes());
            assertEquals(1, cart.getCartItems().size());
            CartItem item = cart.getCartItems().get(0);
            assertEquals(201L, item.getId()); assertEquals(101L, item.getCart().getId());
            assertEquals("legacy-product", item.getProductId()); assertEquals("Legacy", item.getProductName());
            assertEquals(2, item.getQuantity()); assertEquals("EUR", item.getCurrency()); assertEquals("SKU", item.getProductSku());
            assertEquals(new BigDecimal("12.34"), item.getUnitPrice()); assertEquals(new BigDecimal("24.68"), item.getTotalPrice());
            assertEquals(new BigDecimal("1.234"), item.getWeight()); assertNull(item.getProductBrand());
            tx.commit();
        } finally { StandardServiceRegistryBuilder.destroy(registry); }
    }
}
