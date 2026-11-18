package org.de013.orderservice.service.impl;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = {"spring.cloud.config.enabled=false", "spring.cloud.bus.enabled=false", "eureka.client.enabled=false", "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true", "order.checkout-expiry.enabled=false", "order.allocation.recovery-delay-ms=3600000"})
class CompensationStoreTest {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", DB::getJdbcUrl);
        r.add("spring.datasource.username", DB::getUsername);
        r.add("spring.datasource.password", DB::getPassword);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired CompensationStore store;

    private long finding() {
        long id = jdbc.queryForObject("INSERT INTO orders(order_number,user_id,status,order_type) VALUES (?, 'owner','PENDING','STANDARD') RETURNING id", Long.class, UUID.randomUUID().toString());
        jdbc.update("INSERT INTO reconciliation_finding(order_id,current_revision) VALUES (?,1)", id);
        jdbc.update("INSERT INTO reconciliation_revision(order_id,revision,evidence,reasons) VALUES (?,1,'{}','[]')", id);
        return id;
    }

    private UUID submit(long id) {
        UUID request = UUID.randomUUID();
        assertEquals("REQUESTED", jdbc.queryForObject("SELECT compensation_submit(?,?,1,'review')", String.class, request, id));
        return request;
    }

    @Test void idempotencyLeaseAndImmutableAudit() {
        long id = finding(); UUID request = submit(id);
        assertEquals("REQUESTED", jdbc.queryForObject("SELECT compensation_submit(?,?,1,'review')", String.class, request, id));
        assertThrows(DataAccessException.class, () -> jdbc.queryForObject("SELECT compensation_submit(?,?,1,'different')", String.class, request, id));
        assertThrows(DataAccessException.class, () -> submit(id));
        var first = store.claim(Duration.ofMinutes(1));
        assertNotNull(first);
        assertEquals(request, first.requestId());
        jdbc.update("UPDATE compensation_request SET claim_until=clock_timestamp()-interval '1 second', next_check_at=clock_timestamp()-interval '1 second' WHERE request_id=?", request);
        var second = store.claim(Duration.ofMinutes(1));
        assertNotEquals(first.token(), second.token());
        assertFalse(store.hold(first, "old"));
        jdbc.update("UPDATE reconciliation_finding SET current_revision=2 WHERE order_id=?", id);
        assertTrue(store.hold(second, "checked"));
        assertEquals("STALE", jdbc.queryForObject("SELECT status FROM compensation_request WHERE request_id=?", String.class, request));
        assertThrows(DataAccessException.class, () -> jdbc.update("UPDATE compensation_event SET outcome='changed' WHERE request_id=?", request));
        assertThrows(DataAccessException.class, () -> jdbc.update("DELETE FROM compensation_event WHERE request_id=?", request));
    }

    @Test void concurrentClaimsCannotShareOneRequest() throws Exception {
        long id = finding(); UUID request = submit(id);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var a = executor.submit(() -> store.claim(Duration.ofMinutes(1)));
            var b = executor.submit(() -> store.claim(Duration.ofMinutes(1)));
            var first = a.get(10, TimeUnit.SECONDS); var second = b.get(10, TimeUnit.SECONDS);
            assertTrue((first != null && first.requestId().equals(request)) ^ (second != null && second.requestId().equals(request)));
            assertTrue(store.hold(first == null ? second : first, "test"));
        } finally { executor.shutdownNow(); }
    }

    @Test void publicHasNoFunctionOrTablePrivileges() {
        assertFalse(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM pg_proc p CROSS JOIN LATERAL aclexplode(p.proacl) a WHERE p.oid='compensation_submit(uuid,bigint,bigint,text)'::regprocedure AND a.grantee=0 AND a.privilege_type='EXECUTE')", Boolean.class));
        assertFalse(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM pg_class c CROSS JOIN LATERAL aclexplode(c.relacl) a WHERE c.oid='compensation_request'::regclass AND a.grantee=0 AND a.privilege_type='INSERT')", Boolean.class));
    }

    @Test void changedLocalFactsBecomeStaleAndAreRecorded() {
        long id = finding(); UUID request = submit(id);
        var claim = store.claim(Duration.ofMinutes(1));
        assertEquals(request, claim.requestId());
        jdbc.update("UPDATE orders SET status='ON_HOLD' WHERE id=?", id);
        assertTrue(store.hold(claim, "local facts changed"));
        assertEquals("STALE", jdbc.queryForObject("SELECT status FROM compensation_request WHERE request_id=?", String.class, request));
        assertEquals("PENDING/ON_HOLD", jdbc.queryForObject("SELECT (before_state->'order'->>'status') || '/' || (after_state->'order'->>'status') FROM compensation_event WHERE request_id=? AND outcome='STALE'", String.class, request));
    }

    @Test void claimRejectsStaleRevisionBeforeNetworkWork() {
        long id=finding(); UUID request=submit(id);
        jdbc.update("UPDATE reconciliation_finding SET current_revision=2 WHERE order_id=?",id);
        assertNull(store.claim(Duration.ofMinutes(1)));
        assertEquals("STALE",jdbc.queryForObject("SELECT status FROM compensation_request WHERE request_id=?",String.class,request));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM compensation_event WHERE request_id=? AND outcome='STALE'",Integer.class,request));
    }

    @Test void staleOrExpiredClaimCannotFinalizeOrWriteAudit() {
        long id=finding(); UUID request=submit(id); var old=store.claim(Duration.ofMinutes(1));
        jdbc.update("UPDATE compensation_request SET claim_until=clock_timestamp()-interval '1 second',next_check_at=clock_timestamp()-interval '1 second' WHERE request_id=?",request);
        var fresh=store.claim(Duration.ofMinutes(1));
        assertFalse(store.finish(old,"{}","[]"));
        jdbc.update("UPDATE compensation_request SET claim_until=clock_timestamp()-interval '1 second' WHERE request_id=?",request);
        assertFalse(store.finish(fresh,"{}","[]"));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM compensation_event WHERE request_id=? AND outcome='STALE'",Integer.class,request));
        assertEquals(fresh.token(),jdbc.queryForObject("SELECT claim_token FROM compensation_request WHERE request_id=?",UUID.class,request));
    }

    @Test void leaseExpiringWhileWaitingForOrderLockCannotWrite() throws Exception {
        long id=finding(); UUID operation=UUID.randomUUID();
        jdbc.update("INSERT INTO inventory_allocation_intents(operation_id,order_id,target_kind,target_id,quantity,owner_id,state) VALUES (?,?,'PRODUCT',42,1,'owner','RESERVED')",operation,id);
        UUID request=submit(id); var claim=store.claim(Duration.ofSeconds(8));
        int eventsBefore=jdbc.queryForObject("SELECT count(*) FROM compensation_event WHERE request_id=?",Integer.class,request);
        var executor=Executors.newSingleThreadExecutor();
        try(var blocker=DriverManager.getConnection(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword())) {
            blocker.setAutoCommit(false);
            try(var s=blocker.prepareStatement("SELECT id FROM orders WHERE id=? FOR UPDATE")) { s.setLong(1,id); s.execute(); }
            var future=executor.submit(()->store.finish(claim,"{}","[]"));
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock' AND query LIKE '%compensation_finalize%')",Boolean.class)));
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT claim_until<=clock_timestamp() FROM compensation_request WHERE request_id=?",Boolean.class,request)));
            blocker.commit(); assertFalse(future.get(10,TimeUnit.SECONDS));
            assertEquals("CLAIMED",jdbc.queryForObject("SELECT status FROM compensation_request WHERE request_id=?",String.class,request));
            assertEquals(claim.token(),jdbc.queryForObject("SELECT claim_token FROM compensation_request WHERE request_id=?",UUID.class,request));
            assertEquals(eventsBefore,jdbc.queryForObject("SELECT count(*) FROM compensation_event WHERE request_id=?",Integer.class,request));
            assertEquals("RESERVED",jdbc.queryForObject("SELECT state FROM inventory_allocation_intents WHERE operation_id=?",String.class,operation));
        } finally { executor.shutdownNow(); assertTrue(executor.awaitTermination(10,TimeUnit.SECONDS)); }
    }

    @Test void checkedInProvisionScriptGrantsOnlyApprovedResolverRights() throws Exception {
        String suffix=UUID.randomUUID().toString().replace("-","");
        String opGroup="og_"+suffix,resGroup="rg_"+suffix,op="ol_"+suffix,res="rl_"+suffix,password=UUID.randomUUID().toString();
        jdbc.execute("CREATE ROLE "+opGroup+" NOLOGIN"); jdbc.execute("CREATE ROLE "+resGroup+" NOLOGIN");
        jdbc.execute("CREATE ROLE "+op+" LOGIN PASSWORD '"+password+"'"); jdbc.execute("CREATE ROLE "+res+" LOGIN PASSWORD '"+password+"'");
        jdbc.execute("GRANT "+opGroup+" TO "+op); jdbc.execute("GRANT "+resGroup+" TO "+res);
        DB.copyFileToContainer(org.testcontainers.utility.MountableFile.forHostPath(java.nio.file.Path.of("src/main/resources/reconciliation/provision-compensation-roles.sql").toAbsolutePath()),"/tmp/gate11-provision.sql");
        var command=DB.execInContainer("psql","-U",DB.getUsername(),"-d",DB.getDatabaseName(),"-v","operator_group="+opGroup,"-v","resolver_group="+resGroup,"-f","/tmp/gate11-provision.sql");
        assertEquals(0,command.getExitCode(),command.getStderr());
        long id=finding(); UUID request=UUID.randomUUID();
        try(var operator=DriverManager.getConnection(DB.getJdbcUrl(),op,password); var resolver=DriverManager.getConnection(DB.getJdbcUrl(),res,password)) {
            try(var s=operator.prepareStatement("SELECT compensation_submit(?,?,1,'review')")) { s.setObject(1,request); s.setLong(2,id); s.execute(); }
            assertEquals(op,jdbc.queryForObject("SELECT actor FROM compensation_request WHERE request_id=?",String.class,request));
            try(var s=resolver.createStatement(); var rows=s.executeQuery("SELECT count(*) FROM reconciliation_revision")) { assertTrue(rows.next()); }
            assertThrows(java.sql.SQLException.class,()->resolver.createStatement().execute("SELECT compensation_submit('"+UUID.randomUUID()+"',"+id+",1,'forged')"));
            assertThrows(java.sql.SQLException.class,()->resolver.createStatement().execute("UPDATE compensation_request SET actor='forged'"));
            assertThrows(java.sql.SQLException.class,()->resolver.createStatement().execute("DELETE FROM compensation_event"));
            UUID token;
            try(var s=resolver.createStatement(); var rows=s.executeQuery("SELECT * FROM compensation_claim(60000)")) { assertTrue(rows.next()); token=rows.getObject(4,UUID.class); }
            try(var s=resolver.prepareStatement("SELECT compensation_hold(?,?,'test')")) { s.setObject(1,request); s.setObject(2,token); try(var rows=s.executeQuery()) { assertTrue(rows.next()); assertTrue(rows.getBoolean(1)); } }
            assertEquals(res,jdbc.queryForObject("SELECT executor_login FROM compensation_event WHERE request_id=? AND outcome='HOLD'",String.class,request));
        }
    }

    @Test void individualLoginPrivilegesAndAuditIdentity() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String operator = "op_" + suffix, resolver = "res_" + suffix, runtime = "run_" + suffix;
        String password = UUID.randomUUID().toString();
        jdbc.execute("CREATE ROLE " + operator + " LOGIN PASSWORD '" + password + "'");
        jdbc.execute("CREATE ROLE " + resolver + " LOGIN PASSWORD '" + password + "'");
        jdbc.execute("CREATE ROLE " + runtime + " LOGIN PASSWORD '" + password + "'");
        jdbc.execute("GRANT USAGE ON SCHEMA public TO " + operator + "," + resolver + "," + runtime);
        jdbc.execute("GRANT EXECUTE ON FUNCTION compensation_submit(uuid,bigint,bigint,text) TO " + operator);
        jdbc.execute("GRANT EXECUTE ON FUNCTION compensation_claim(bigint), compensation_hold(uuid,uuid,text) TO " + resolver);
        long id = finding(); UUID request = UUID.randomUUID();
        try (var op = DriverManager.getConnection(DB.getJdbcUrl(), operator, password);
             var res = DriverManager.getConnection(DB.getJdbcUrl(), resolver, password);
             var run = DriverManager.getConnection(DB.getJdbcUrl(), runtime, password)) {
            try (var statement = op.prepareStatement("SELECT compensation_submit(?,?,1,'review')")) {
                statement.setObject(1, request); statement.setLong(2, id); statement.execute();
            }
            assertEquals(operator, jdbc.queryForObject("SELECT actor FROM compensation_request WHERE request_id=?", String.class, request));
            assertThrows(java.sql.SQLException.class, () -> op.createStatement().execute("UPDATE compensation_event SET actor='forged'"));
            assertThrows(java.sql.SQLException.class, () -> run.createStatement().execute("SELECT compensation_claim(1000)"));
            assertThrows(java.sql.SQLException.class, () -> res.createStatement().execute("INSERT INTO compensation_event(request_id,actor,db_principal,action,outcome,before_state,after_state) VALUES ('" + request + "','x','x','x','x','{}','{}')"));
            UUID token;
            try (var statement = res.createStatement(); var rows = statement.executeQuery("SELECT * FROM compensation_claim(60000)")) {
                assertTrue(rows.next()); assertEquals(request, rows.getObject(1, UUID.class)); token = rows.getObject(4, UUID.class);
            }
            try (var statement = res.prepareStatement("SELECT compensation_hold(?,?,'checked')")) {
                statement.setObject(1, request); statement.setObject(2, token);
                try (var rows = statement.executeQuery()) { assertTrue(rows.next()); assertTrue(rows.getBoolean(1)); }
            }
            assertEquals(operator, jdbc.queryForObject("SELECT actor FROM compensation_event WHERE request_id=? AND outcome='HOLD'", String.class, request));
            assertEquals(resolver, jdbc.queryForObject("SELECT executor_login FROM compensation_event WHERE request_id=? AND outcome='HOLD'", String.class, request));
            assertEquals(DB.getUsername(), jdbc.queryForObject("SELECT db_principal FROM compensation_event WHERE request_id=? AND outcome='HOLD'", String.class, request));
        }
    }

    @Test void snapshotAndAuditExcludeSensitiveFields() {
        long id = finding();
        jdbc.update("UPDATE orders SET shipping_first_name='secretname', shipping_street_address='secretaddress', shipping_email='secretemail', internal_notes='secretnote' WHERE id=?", id);
        jdbc.update("INSERT INTO order_items(order_id,product_id,sku,product_name,product_description,quantity) VALUES (?,42,'secret-sku','secret-product','secret-description',1)", id);
        UUID request = submit(id);
        var claim = store.claim(Duration.ofMinutes(1));
        assertEquals(request, claim.requestId());
        assertFalse(claim.localSnapshot().contains("secret"));
        assertTrue(store.hold(claim, "checked"));
        assertFalse(jdbc.queryForObject("SELECT bool_or(before_state::text LIKE '%secret%' OR after_state::text LIKE '%secret%') FROM compensation_event WHERE request_id=?", Boolean.class, request));
    }

    @Test void submitHoldAndDetectionLockOverlapCompletesWithoutDeadlock() throws Exception {
        long id = finding(); UUID request = submit(id);
        var claim = store.claim(Duration.ofMinutes(1));
        assertEquals(request, claim.requestId());
        var pool = Executors.newFixedThreadPool(3);
        try (var blocker = DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword())) {
            blocker.setAutoCommit(false);
            try (var statement = blocker.prepareStatement("SELECT id FROM orders WHERE id=? FOR UPDATE")) {
                statement.setLong(1, id); statement.execute();
            }
            var started = new java.util.concurrent.CountDownLatch(3);
            var hold = pool.submit(() -> { started.countDown(); return store.hold(claim, "checked"); });
            var submit = pool.submit(() -> { started.countDown(); return jdbc.queryForObject("SELECT compensation_submit(?,?,1,'second')", String.class, UUID.randomUUID(), id); });
            var detection = pool.submit(() -> { started.countDown(); return jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>) connection -> {
                connection.setAutoCommit(false);
                try (var order = connection.prepareStatement("SELECT id FROM orders WHERE id=? FOR UPDATE");
                     var finding = connection.prepareStatement("SELECT order_id FROM reconciliation_finding WHERE order_id=? FOR UPDATE")) {
                    order.setLong(1, id); order.execute(); finding.setLong(1, id); finding.execute(); connection.commit(); return true;
                } finally { connection.setAutoCommit(true); }
            }); });
            assertTrue(started.await(10, TimeUnit.SECONDS));
            blocker.commit();
            assertTrue(hold.get(10, TimeUnit.SECONDS));
            try { assertEquals("REQUESTED", submit.get(10, TimeUnit.SECONDS)); }
            catch (java.util.concurrent.ExecutionException e) { assertInstanceOf(DataAccessException.class, e.getCause()); }
            assertTrue(detection.get(10, TimeUnit.SECONDS));
            jdbc.update("UPDATE compensation_request SET status='HOLD' WHERE order_id=? AND status='REQUESTED'", id);
        } finally { pool.shutdownNow(); }
    }
}
