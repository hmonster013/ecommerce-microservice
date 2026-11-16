package org.de013.shoppingcart;

import org.de013.shoppingcart.config.CouponMigrationGuard;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.PostgreSQLContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CartOperatorPreflightTest {
    @TempDir Path temp;

    @Test
    void realOperatorPreflight() throws Exception {
        String name = "ecommerce-cart-preflight-" + UUID.randomUUID();
        try (var pg = new PostgreSQLContainer<>("postgres:17-alpine")
                .withCreateContainerCmdModifier(cmd -> cmd.withName(name))) {
            pg.start();
            String base = pg.getJdbcUrl().replaceAll("/[^/]+$", "/");
            try (var c = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword()); var s = c.createStatement()) {
                s.execute("create database reference");
                s.execute("create database target");
                s.execute("create role cart_operator login");
            }
            // Disposable fixture only: the operator needs EXECUTE on pg_control_system and SELECT on cart tables/history.
            for (String db : new String[]{"reference", "target"}) {
                sql(base + db, pg, "revoke execute on function pg_catalog.pg_control_system() from public; grant execute on function pg_catalog.pg_control_system() to cart_operator");
            }
            Path sql = Path.of("src/main/resources/db/cart-preflight.sql").toAbsolutePath();
            assertEquals(0, new ProcessBuilder("docker", "cp", sql.toString(), name + ":/tmp/cart-preflight.sql").inheritIO().start().waitFor());
            Files.writeString(temp.resolve("psql.cmd"), "@echo off\r\npowershell -NoProfile -ExecutionPolicy RemoteSigned -File \"%~dp0shim.ps1\" %*\r\nexit /b %errorlevel%\r\n");
            Files.writeString(temp.resolve("shim.ps1"), "$a = @($args); for ($i=0; $i -lt $a.Count; $i++) { if ($a[$i] -eq '-f') { $a[$i+1] = '/tmp/cart-preflight.sql' } }; & docker exec -i -e PGUSER=$env:CART_PGUSER " + name + " psql @a; exit $LASTEXITCODE\n");
            Flyway ref1 = flyway(base + "reference", pg, "1");
            Flyway target1 = flyway(base + "target", pg, "1");
            ref1.migrate(); target1.migrate(); ref1.validate();
            sql(base + "reference", pg, "grant select on all tables in schema public to cart_operator");
            sql(base + "target", pg, "grant select on all tables in schema public to cart_operator");
            assertResult("reference", "target", "1", "cart_operator", true, "MATCH V1");
            assertResult("reference", "reference", "1", pg.getUsername(), false, "distinct database");
            assertResult("reference", "target", "3", pg.getUsername(), false, "Reference history");
            sql(base + "target", pg, "insert into carts(coupon_code) values ('keep')");
            String before = value(base + "target", pg, "select (select json_agg(c) from carts c)::text || (select json_agg(h) from flyway_schema_history h)::text");
            assertResult("reference", "target", "1", pg.getUsername(), false, "Cart preflight failed");
            assertEquals(before, value(base + "target", pg, "select (select json_agg(c) from carts c)::text || (select json_agg(h) from flyway_schema_history h)::text"));
            sql(base + "target", pg, "delete from carts; insert into carts(id,user_id,currency,subtotal,total_amount,item_count,total_quantity,notes,coupon_code) values (101,'123e4567-e89b-12d3-a456-426614174000','EUR',24.68,24.68,1,2,'keep note',null); insert into cart_items(id,cart_id,product_id,product_name,quantity,unit_price,total_price,currency,product_sku,weight) values (201,101,'legacy-product','Legacy',2,12.34,24.68,'EUR','SKU',1.234); drop table flyway_schema_history");
            ref1.validate();
            assertResult("reference", "target", "1", "cart_operator", true, "MATCH V1");
            Flyway adopted = Flyway.configure().dataSource(base + "target", pg.getUsername(), pg.getPassword())
                    .baselineVersion("1").baselineOnMigrate(false).callbacks(new CouponMigrationGuard()).load();
            adopted.baseline();
            adopted.migrate();
            String history = value(base + "target", pg, "select string_agg(version || ':' || type || ':' || success, ',' order by installed_rank) from flyway_schema_history where version is not null");
            assertEquals("1:BASELINE:true,2:SQL:true,3:SQL:true", history);
            assertEquals("123e4567-e89b-12d3-a456-426614174000|EUR|24.68|24.68|1|2|keep note|201|legacy-product|Legacy|2|12.34|24.68|EUR|SKU|1.234|true",
                    value(base + "target", pg, "select concat_ws('|',c.user_id,c.currency,c.subtotal,c.total_amount,c.item_count,c.total_quantity,c.notes,i.id,i.product_id,i.product_name,i.quantity,i.unit_price,i.total_price,i.currency,i.product_sku,i.weight,(i.product_brand is null)::text) from carts c join cart_items i on i.cart_id=c.id where c.id=101"));
            adopted.validate();
            adopted.migrate();
            assertEquals(history, value(base + "target", pg, "select string_agg(version || ':' || type || ':' || success, ',' order by installed_rank) from flyway_schema_history where version is not null"));
            flyway(base + "reference", pg, "3").migrate();
            Flyway ref3 = flyway(base + "reference", pg, "3"); ref3.validate();
            assertResult("reference", "target", "3", pg.getUsername(), true, "MATCH V3");
            assertResult("reference", "target", "1", pg.getUsername(), false, "Reference history");
            sql(base + "target", pg, "alter table cart_items drop constraint fk_cart_item_cart; alter table cart_items add constraint fk_cart_item_cart foreign key (cart_id) references carts(id) on delete cascade not valid");
            assertResult("reference", "target", "3", pg.getUsername(), false, "fingerprint mismatch");
            sql(base + "target", pg, "alter table cart_items validate constraint fk_cart_item_cart; alter table carts add constraint test_check check (id > 0) not valid");
            assertResult("reference", "target", "3", pg.getUsername(), false, "fingerprint mismatch");
            sql(base + "target", pg, "alter table carts drop constraint test_check; create table index_fixture (value integer); insert into index_fixture values (1), (1)");
            sql(base + "reference", pg, "create table index_fixture (value integer); create unique index fixture_idx on index_fixture(value)");
            assertThrows(java.sql.SQLException.class, () -> sql(base + "target", pg, "create unique index concurrently fixture_idx on index_fixture(value)"));
            assertEquals("false", value(base + "target", pg, "select indisvalid::text from pg_index where indexrelid='fixture_idx'::regclass"));
            assertResult("reference", "target", "3", pg.getUsername(), false, "fingerprint mismatch");
            sql(base + "reference", pg, "update flyway_schema_history set checksum=checksum+1 where version='1'");
            assertThrows(FlywayException.class, ref3::validate);
            sql(base + "reference", pg, "update flyway_schema_history set type='BASELINE' where version='1'");
            assertResult("reference", "target", "3", pg.getUsername(), false, "Reference history");
            sql(base + "reference", pg, "revoke execute on function pg_catalog.pg_control_system() from cart_operator");
            assertResult("reference", "target", "3", "cart_operator", false, "query failed");
        }
    }

    private Flyway flyway(String url, PostgreSQLContainer<?> pg, String version) {
        return Flyway.configure().dataSource(url, pg.getUsername(), pg.getPassword()).target(version).load();
    }
    private void sql(String url, PostgreSQLContainer<?> pg, String sql) throws Exception {
        try (var c = DriverManager.getConnection(url, pg.getUsername(), pg.getPassword()); var s = c.createStatement()) { s.execute(sql); }
    }
    private String value(String url, PostgreSQLContainer<?> pg, String sql) throws Exception {
        try (var c = DriverManager.getConnection(url, pg.getUsername(), pg.getPassword()); var s = c.createStatement(); var r = s.executeQuery(sql)) { assertTrue(r.next()); return r.getString(1); }
    }
    private void assertResult(String reference, String target, String version, String user, boolean success, String expected) throws Exception {
        var command = new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "RemoteSigned", "-File", Path.of("src/main/resources/db/cart-preflight.ps1").toAbsolutePath().toString(), "-ReferenceDatabase", reference, "-TargetDatabase", target, "-Version", version).redirectErrorStream(true);
        command.environment().put("PATH", temp + ";" + System.getenv("PATH"));
        command.environment().put("CART_PGUSER", user);
        var p = command.start();
        String output = new String(p.getInputStream().readAllBytes());
        int exit = p.waitFor();
        assertEquals(success, exit == 0, output);
        assertTrue(output.contains(expected), output);
    }
}
