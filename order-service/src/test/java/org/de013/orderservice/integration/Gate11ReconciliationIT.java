package org.de013.orderservice.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

class Gate11ReconciliationIT {
    @Test @Timeout(value=12,unit=TimeUnit.MINUTES)
    void knownLateChargeReopensCompensation() throws Exception { scenario(false,false); }

    @Test @Timeout(value=12,unit=TimeUnit.MINUTES)
    void unresolvedDisputeReopensCompensation() throws Exception { scenario(true,false); }

    @Test @Timeout(value=12,unit=TimeUnit.MINUTES)
    void paymentProofThenStaleRequestAndConflictRemainFenced() throws Exception { scenario(false,true); }

    private void scenario(boolean unresolved,boolean staleConflict) throws Exception {
        String password=UUID.randomUUID().toString(), secret=UUID.randomUUID().toString();
        try (var catalogDb=new PostgreSQLContainer<>("postgres:17-alpine").withDatabaseName("catalog_gate11");
             var paymentDb=new PostgreSQLContainer<>("postgres:17-alpine").withDatabaseName("payment_gate11");
             var orderDb=new PostgreSQLContainer<>("postgres:17-alpine").withDatabaseName("order_gate11");
             var redis=new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
             var kafka=new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));
             var keycloak=new GenericContainer<>(DockerImageName.parse("quay.io/keycloak/keycloak:26.0.7"))
                     .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME","admin").withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD",password)
                     .withCommand("start-dev").withExposedPorts(8080).waitingFor(Wait.forHttp("/realms/master").forStatusCode(200))) {
            catalogDb.start(); paymentDb.start(); orderDb.start(); redis.start(); kafka.start(); keycloak.start();
            String base="http://"+keycloak.getHost()+":"+keycloak.getMappedPort(8080);
            var kc=new CrossServiceCheckoutIT.Keycloak(base,password,secret);
            String issuer=base+"/realms/checkout-test", tokenUri=issuer+"/protocol/openid-connect/token";
            Path temp=Path.of(System.getProperty("java.io.tmpdir"),"opencode"); Files.createDirectories(temp);
            Path isolated=Files.createTempDirectory(temp,"gate11-launcher-");
            String resource=GenericExternalApplicationLauncher.class.getName().replace('.','/')+".class";
            Path copy=isolated.resolve(resource); Files.createDirectories(copy.getParent());
            try(var in=GenericExternalApplicationLauncher.class.getClassLoader().getResourceAsStream(resource)) { assertNotNull(in); Files.copy(in,copy); }
            var posts=new AtomicInteger(); var gets=new AtomicInteger();
            HttpServer stripe=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            stripe.createContext("/",exchange->{
                String path=exchange.getRequestURI().getPath();
                if(!"GET".equals(exchange.getRequestMethod())) { posts.incrementAndGet(); exchange.sendResponseHeaders(405,-1); exchange.close(); return; }
                gets.incrementAndGet();
                String body=switch(path) {
                    case "/v1/account" -> "{\"id\":\"acct_fixture\",\"object\":\"account\"}";
                    case "/v1/payment_intents/pi_fixture" -> "{\"id\":\"pi_fixture\",\"object\":\"payment_intent\",\"livemode\":false,\"amount\":1250,\"amount_received\":1250,\"currency\":\"usd\",\"status\":\"succeeded\"}";
                    case "/v1/charges" -> "{\"object\":\"list\",\"has_more\":false,\"data\":[{\"id\":\"ch_fixture\",\"object\":\"charge\",\"payment_intent\":\"pi_fixture\",\"livemode\":false,\"amount\":1250,\"amount_captured\":1250,\"amount_refunded\":1250,\"captured\":true,\"paid\":true,\"status\":\"succeeded\",\"currency\":\"usd\"}]}";
                    case "/v1/refunds" -> "{\"object\":\"list\",\"has_more\":false,\"data\":[{\"id\":\"re_fixture\",\"object\":\"refund\",\"payment_intent\":\"pi_fixture\",\"charge\":\"ch_fixture\",\"amount\":1250,\"currency\":\"usd\",\"status\":\"succeeded\",\"livemode\":false}]}";
                    default -> null;
                };
                if(body==null) { exchange.sendResponseHeaders(404,-1); exchange.close(); return; }
                byte[] data=body.getBytes(StandardCharsets.UTF_8); exchange.getResponseHeaders().add("Content-Type","application/json");
                exchange.sendResponseHeaders(200,data.length); exchange.getResponseBody().write(data); exchange.close();
            }); stripe.start();
            try (var catalog=new CrossServiceCheckoutIT.App(temp,isolated,"product-catalog-service","org.de013.productcatalog.ProductCatalogApplication",catalogDb,
                    "--spring.security.oauth2.resourceserver.jwt.issuer-uri="+issuer,"--spring.data.redis.host="+redis.getHost(),"--spring.data.redis.port="+redis.getMappedPort(6379));
                 var payment=new CrossServiceCheckoutIT.App(temp,isolated,"payment-service","org.de013.paymentservice.PaymentServiceApplication",paymentDb,
                    "--payment.internal.issuer-uri="+issuer,"--payment.gateways.stripe.api-key=sk_test_gate11_fixture","--payment.gateways.stripe.webhook-secret=whsec_gate11_fixture",
                    "--spring.kafka.bootstrap-servers="+kafka.getBootstrapServers(),
                    "--payment.compensation.enabled=true","--payment.compensation.stripe.expected-account-id=acct_fixture","stripe-base=http://127.0.0.1:"+stripe.getAddress().getPort());
                 var proxy=new PaymentProxy(payment.port);
                 var order=new CrossServiceCheckoutIT.App(temp,isolated,"order-service","org.de013.orderservice.OrderServiceApplication",orderDb,
                    "--order.compensation.enabled=false","--order.reconciliation.enabled=false","--order.checkout-expiry.enabled=false","--order.allocation.recovery-delay-ms=3600000",
                    "--order.payment-events.enabled=false",
                    "--spring.kafka.bootstrap-servers="+kafka.getBootstrapServers(),
                    "--spring.cloud.openfeign.client.config.inventoryReservation.url=http://127.0.0.1:"+catalog.port,
                    "--spring.cloud.openfeign.client.config.paymentReconciliation.url=http://127.0.0.1:"+proxy.port(),
                    "--spring.security.oauth2.client.registration.catalog-inventory.client-id=order-service","--spring.security.oauth2.client.registration.catalog-inventory.client-secret="+secret,
                    "--spring.security.oauth2.client.registration.catalog-inventory.authorization-grant-type=client_credentials","--spring.security.oauth2.client.provider.catalog-inventory.token-uri="+tokenUri,
                    "--spring.security.oauth2.client.registration.payment-reconciliation.client-id=order-service","--spring.security.oauth2.client.registration.payment-reconciliation.client-secret="+secret,
                    "--spring.security.oauth2.client.registration.payment-reconciliation.authorization-grant-type=client_credentials","--spring.security.oauth2.client.provider.payment-reconciliation.token-uri="+tokenUri);
                 Connection c=DriverManager.getConnection(catalogDb.getJdbcUrl(),catalogDb.getUsername(),catalogDb.getPassword());
                 Connection p=DriverManager.getConnection(paymentDb.getJdbcUrl(),paymentDb.getUsername(),paymentDb.getPassword());
                 Connection o=DriverManager.getConnection(orderDb.getJdbcUrl(),orderDb.getUsername(),orderDb.getPassword())) {
                String resolverPassword=UUID.randomUUID().toString();
                exec(o,"CREATE ROLE gate11_resolver LOGIN PASSWORD '"+resolverPassword+"'");
                exec(o,"GRANT USAGE ON SCHEMA public TO gate11_resolver");
                exec(o,"GRANT SELECT ON reconciliation_revision, orders TO gate11_resolver");
                exec(o,"GRANT EXECUTE ON FUNCTION compensation_claim(bigint),compensation_hold(uuid,uuid,text),compensation_retry(uuid,uuid,bigint),compensation_finalize(uuid,uuid,jsonb,jsonb),compensation_recurrence(uuid,uuid,text,bigint) TO gate11_resolver");
                try(Connection runtime=DriverManager.getConnection(orderDb.getJdbcUrl(),"gate11_resolver",resolverPassword)) {
                    assertThrows(java.sql.SQLException.class,()->exec(runtime,"SELECT compensation_submit('"+UUID.randomUUID()+"',1,1,'forged')"));
                    assertThrows(java.sql.SQLException.class,()->exec(runtime,"DELETE FROM compensation_event"));
                    assertThrows(java.sql.SQLException.class,()->exec(runtime,"UPDATE compensation_request SET actor='forged'"));
                }
                order.close();
                try(var runtimeOrder=order.restartAs("gate11_resolver",resolverPassword)) {
                long product=number(c,"INSERT INTO products(name,sku,price) VALUES ('fixture','"+UUID.randomUUID()+"',12.50) RETURNING id");
                exec(c,"UPDATE inventory SET quantity=10 WHERE product_id="+product);
                assertEquals(10,number(c,"SELECT quantity FROM inventory WHERE product_id="+product));
                UUID intent=UUID.randomUUID(),close=UUID.randomUUID(),operation=UUID.randomUUID();
                String reservation="{\"operationId\":\""+intent+"\",\"targetKind\":\"PRODUCT\",\"targetId\":"+product+",\"quantity\":1,\"owner\":\"owner\"}";
                String token=kc.token(secret), url="http://127.0.0.1:"+catalog.port+"/internal/inventory-reservations";
                assertEquals(200,post(url,token,reservation).statusCode()); assertEquals(200,post(url+"/"+intent+"/release",token,"").statusCode());
                String key=UUID.randomUUID().toString();
                exec(o,"INSERT INTO checkout_attempts(attempt_key,user_id,request_snapshot) VALUES ('"+key+"','owner','{\"userId\":\"owner\",\"currency\":\"USD\",\"paymentMethod\":\"CARD\"}')");
                long id=number(o,"INSERT INTO orders(order_number,user_id,status,order_type,allocation_phase,checkout_key,checkout_close_operation,total_amount,currency) VALUES ('"+UUID.randomUUID()+"','owner','CANCELLED','STANDARD','CLOSED','"+key+"','"+close+"',12.50,'USD') RETURNING id");
                exec(o,"INSERT INTO order_items(order_id,product_id,sku,product_name,quantity,status) VALUES ("+id+",'"+product+"','sku','fixture',1,'PENDING')");
                exec(o,"INSERT INTO inventory_allocation_intents(operation_id,order_id,target_kind,target_id,quantity,owner_id,state) VALUES ('"+intent+"',"+id+",'PRODUCT',"+product+",1,'owner','RESERVED')");
                long paymentId=number(p,"INSERT INTO payments(payment_number,order_id,user_id,amount,currency,status,method,gateway_name,stripe_payment_intent_id,stripe_intent_livemode,stripe_intent_created_by_service) VALUES ('pay_fixture',"+id+",'owner',12.50,'USD','SUCCEEDED','CARD','STRIPE','pi_fixture',false,true) RETURNING id");
                exec(p,"INSERT INTO payment_order_guard(order_id,reconciliation_hold,close_operation_id,close_status,close_reason) VALUES ("+id+",true,'"+close+"','HOLD','LATE_SUCCESS')");
                String evidence="{\"payment\":{\"orderId\":"+id+",\"observedAt\":\"2026-01-01T00:00:00Z\",\"guardPresent\":true,\"reconciliationHold\":true,\"closeOperationId\":\""+close+"\",\"closeStatus\":\"HOLD\",\"closeReason\":\"LATE_SUCCESS\",\"closeSafeAt\":null,\"closeSafeProofPresent\":false,\"attempts\":[{\"paymentId\":"+paymentId+",\"paymentNumber\":\"pay_fixture\",\"status\":\"SUCCEEDED\",\"amount\":12.50,\"currency\":\"USD\",\"gateway\":\"STRIPE\",\"successfulChargeOrCapture\":false}]}}";
                exec(o,"INSERT INTO reconciliation_finding(order_id,current_revision) VALUES ("+id+",1)");
                try(var stmt=o.prepareStatement("INSERT INTO reconciliation_revision(order_id,revision,evidence,reasons) VALUES (?,1,CAST(? AS jsonb),'[\"HOLD_WITH_INVENTORY\"]')")) { stmt.setLong(1,id); stmt.setString(2,evidence); stmt.execute(); }
                String opPassword=UUID.randomUUID().toString(); exec(o,"CREATE ROLE gate11_operator LOGIN PASSWORD '"+opPassword+"'");
                exec(o,"GRANT USAGE ON SCHEMA public TO gate11_operator");
                exec(o,"GRANT EXECUTE ON FUNCTION compensation_submit(uuid,bigint,bigint,text) TO gate11_operator");
                try(Connection operator=DriverManager.getConnection(orderDb.getJdbcUrl(),"gate11_operator",opPassword); var stmt=operator.prepareStatement("SELECT compensation_submit(?,?,1,'external refund verified')")) { stmt.setObject(1,operation); stmt.setLong(2,id); stmt.execute(); }
                assertEquals("gate11_operator",text(o,"SELECT actor FROM compensation_request WHERE request_id='"+operation+"'"));
                String proofUrl="http://127.0.0.1:"+payment.port+"/internal/reconciliation/orders/"+id+"/compensation-proofs";
                String proofRequest="{\"operationId\":\""+operation+"\",\"findingRevision\":1,\"expectedPaymentId\":"+paymentId+",\"expectedPaymentNumber\":\"pay_fixture\",\"expectedAmount\":12.50,\"expectedCurrency\":\"USD\",\"expectedCloseOperationId\":\""+close+"\"}";
                String readOnly=kc.paymentRoleToken(secret,"payment-reconciliation-resolve");
                assertEquals(401,post(proofUrl,null,proofRequest).statusCode());
                assertEquals(403,post(proofUrl,readOnly,proofRequest).statusCode());
                exec(c,"CREATE TABLE gate11_inventory_audit(table_name text NOT NULL)");
                exec(c,"CREATE FUNCTION gate11_count_inventory_mutation() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN INSERT INTO gate11_inventory_audit VALUES (TG_TABLE_NAME); RETURN NULL; END $$");
                exec(c,"CREATE TRIGGER gate11_inventory_mutation AFTER INSERT OR UPDATE OR DELETE ON inventory FOR EACH STATEMENT EXECUTE FUNCTION gate11_count_inventory_mutation()");
                exec(c,"CREATE TRIGGER gate11_reservation_mutation AFTER INSERT OR UPDATE OR DELETE ON inventory_reservations FOR EACH STATEMENT EXECUTE FUNCTION gate11_count_inventory_mutation()");
                String inventoryBefore=text(c,"SELECT quantity::text || '/' || reserved_quantity::text FROM inventory WHERE product_id="+product);
                int beforePosts=posts.get(), beforeGets=gets.get(); runtimeOrder.compensate();
                assertEquals(1,proxy.requests.size());
                assertEquals("COMPENSATED",text(p,"SELECT status FROM compensation_operation WHERE order_id="+id));
                assertEquals(1,number(p,"SELECT count(*) FROM compensation_proof WHERE operation_id='"+operation+"'"));
                assertEquals("REQUESTED",text(o,"SELECT status FROM compensation_request WHERE request_id='"+operation+"'"));
                assertEquals("RESERVED",text(o,"SELECT state FROM inventory_allocation_intents WHERE operation_id='"+intent+"'"));
                int afterProofGets=gets.get();
                if(staleConflict) {
                    exec(o,"INSERT INTO reconciliation_revision(order_id,revision,evidence,reasons) SELECT order_id,2,evidence,reasons FROM reconciliation_revision WHERE order_id="+id+" AND revision=1");
                    exec(o,"UPDATE reconciliation_finding SET current_revision=2 WHERE order_id="+id);
                    exec(o,"UPDATE compensation_request SET next_check_at=clock_timestamp()-interval '1 second' WHERE request_id='"+operation+"'");
                    runtimeOrder.compensate();
                    assertEquals("STALE",text(o,"SELECT status FROM compensation_request WHERE request_id='"+operation+"'"));
                    assertEquals(1,number(o,"SELECT count(*) FROM compensation_event WHERE request_id='"+operation+"' AND outcome='STALE'"));
                    assertEquals(1,proxy.requests.size());
                    UUID next=UUID.randomUUID();
                    try(Connection operator=DriverManager.getConnection(orderDb.getJdbcUrl(),"gate11_operator",opPassword); var stmt=operator.prepareStatement("SELECT compensation_submit(?,?,2,'new review')")) { stmt.setObject(1,next); stmt.setLong(2,id); stmt.execute(); }
                    runtimeOrder.compensate();
                    assertEquals(2,proxy.requests.size());
                    assertEquals("HOLD",text(o,"SELECT status FROM compensation_request WHERE request_id='"+next+"'"));
                    assertEquals(1,number(o,"SELECT count(*) FROM compensation_event WHERE request_id='"+next+"' AND action='PAYMENT_OPERATION_CONFLICT' AND outcome='HOLD'"));
                    assertEquals("COMPENSATED",text(p,"SELECT status FROM compensation_operation WHERE order_id="+id));
                    assertEquals(1,number(p,"SELECT count(*) FROM compensation_proof WHERE operation_id='"+operation+"'"));
                    assertEquals("true/true",text(p,"SELECT reconciliation_hold::text || '/' || permanent_fence::text FROM payment_order_guard WHERE order_id="+id));
                    assertEquals("RESERVED",text(o,"SELECT state FROM inventory_allocation_intents WHERE operation_id='"+intent+"'"));
                    assertEquals(afterProofGets,gets.get()); assertEquals(beforePosts,posts.get());
                    assertEquals(0,number(c,"SELECT count(*) FROM gate11_inventory_audit"));
                    return;
                }
                exec(o,"UPDATE compensation_request SET next_check_at=clock_timestamp()-interval '1 second' WHERE request_id='"+operation+"'");
                runtimeOrder.compensate();
                assertEquals(2,proxy.requests.size());
                assertEquals(proxy.requests.get(0),proxy.requests.get(1));
                assertEquals(operation.toString(),new ObjectMapper().readTree(proxy.requests.get(0)).path("operationId").asText());
                assertEquals(afterProofGets,gets.get(),"replay must reuse committed Payment proof without provider verification");
                assertEquals("COMPENSATED",text(o,"SELECT status FROM compensation_request WHERE request_id='"+operation+"'"));
                assertNotNull(text(o,"SELECT proof_id::text FROM compensation_request WHERE request_id='"+operation+"'"));
                assertEquals("CANCELLED/CLOSED",text(o,"SELECT status || '/' || allocation_phase FROM orders WHERE id="+id));
                assertEquals("RELEASED",text(o,"SELECT state FROM inventory_allocation_intents WHERE operation_id='"+intent+"'"));
                assertEquals("true/true",text(p,"SELECT reconciliation_hold::text || '/' || permanent_fence::text FROM payment_order_guard WHERE order_id="+id));
                assertEquals(inventoryBefore,text(c,"SELECT quantity::text || '/' || reserved_quantity::text FROM inventory WHERE product_id="+product));
                assertEquals(0,number(c,"SELECT count(*) FROM gate11_inventory_audit"));
                assertEquals(1,number(o,"SELECT count(*) FROM compensation_event WHERE request_id='"+operation+"' AND outcome='COMPENSATED'"));
                assertEquals("gate11_resolver",text(o,"SELECT executor_login FROM compensation_event WHERE request_id='"+operation+"' AND outcome='COMPENSATED'"));
                assertEquals(orderDb.getUsername(),text(o,"SELECT db_principal FROM compensation_event WHERE request_id='"+operation+"' AND outcome='COMPENSATED'"));
                assertEquals(1,number(p,"SELECT count(*) FROM compensation_proof WHERE operation_id='"+operation+"'"));
                assertThrows(java.sql.SQLException.class,()->exec(p,"DELETE FROM compensation_proof WHERE operation_id='"+operation+"'"));
                var readResponse=get(proofUrl+"/"+operation,kc.paymentRoleToken(secret,"payment-reconciliation-resolve"));
                assertEquals(200,readResponse.statusCode());
                assertEquals("COMPENSATED",new ObjectMapper().readTree(readResponse.body()).path("status").asText());
                assertEquals(beforePosts,posts.get()); assertTrue(gets.get()>beforeGets);
                String proofId=text(o,"SELECT proof_id::text FROM compensation_request WHERE request_id='"+operation+"'");
                long version=number(p,"SELECT financial_version FROM payment_order_guard WHERE order_id="+id);
                runtimeOrder.close();
                try (var restarted=runtimeOrder.restart()) {
                    String eventId="evt_"+UUID.randomUUID().toString().replace("-","");
                    var webhook=lateFinancialEvent(payment.port,unresolved,eventId);
                    assertTrue(webhook.statusCode()>=200 && webhook.statusCode()<300,webhook.body());
                    long currentVersion=number(p,"SELECT financial_version FROM payment_order_guard WHERE order_id="+id);
                    if(unresolved) {
                        assertEquals(version,currentVersion);
                        assertEquals(1,number(p,"SELECT count(*) FROM stripe_financial_observation WHERE order_id IS NULL AND event_type='charge.dispute.updated' AND event_id='"+eventId+"'"));
                    } else assertTrue(currentVersion>version);
                    var changed=get(proofUrl+"/"+operation,kc.paymentRoleToken(secret,"payment-reconciliation-resolve"));
                    assertEquals(200,changed.statusCode());
                    var current=new ObjectMapper().readTree(changed.body());
                    assertEquals("HOLD",current.path("status").asText());
                    assertEquals(proofId,current.path("proofId").asText());
                    if(unresolved) {
                        assertEquals("UNRESOLVED_FINANCIAL_OBSERVATION",current.path("reason").asText());
                        assertEquals(version,current.path("financialVersion").asLong());
                        assertEquals(version,current.path("proofVersion").asLong());
                    }
                    exec(o,"UPDATE compensation_request SET next_check_at=clock_timestamp()-interval '1 second' WHERE request_id='"+operation+"'");
                    restarted.compensate();
                    assertEquals("HOLD",text(o,"SELECT status FROM compensation_request WHERE request_id='"+operation+"'"));
                    assertEquals(proofId,text(o,"SELECT proof_id::text FROM compensation_request WHERE request_id='"+operation+"'"));
                    assertEquals(1,number(o,"SELECT count(*) FROM compensation_event WHERE request_id='"+operation+"' AND outcome='COMPENSATED'"));
                    assertEquals(1,number(o,"SELECT count(*) FROM compensation_event WHERE request_id='"+operation+"' AND outcome='HOLD'"));
                    assertEquals("gate11_resolver",text(o,"SELECT executor_login FROM compensation_event WHERE request_id='"+operation+"' AND outcome='HOLD'"));
                    assertEquals(orderDb.getUsername(),text(o,"SELECT db_principal FROM compensation_event WHERE request_id='"+operation+"' AND outcome='HOLD'"));
                    assertEquals(0,number(c,"SELECT count(*) FROM gate11_inventory_audit"));
                    assertEquals(beforePosts,posts.get());
                }
                }
            } finally { stripe.stop(0); }
        }
    }
    private static HttpResponse<String> post(String url,String token,String body) throws Exception {
        var request=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).header("Content-Type","application/json");
        if(token!=null) request.header("Authorization","Bearer "+token);
        else request.header("X-User-Id","order-service").header("X-User-Roles","payment-reconciliation-resolve");
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build().send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    private static final class PaymentProxy implements AutoCloseable {
        private final HttpServer server;
        private final java.util.List<String> requests=new java.util.concurrent.CopyOnWriteArrayList<>();
        private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        PaymentProxy(int port) throws Exception {
            server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/",exchange->{
                try {
                    byte[] body=exchange.getRequestBody().readNBytes(65537);
                    if(body.length>65536) { exchange.sendResponseHeaders(413,-1); return; }
                    var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+exchange.getRequestURI())).timeout(Duration.ofSeconds(15));
                    String auth=exchange.getRequestHeaders().getFirst("Authorization"); if(auth!=null) builder.header("Authorization",auth);
                    String type=exchange.getRequestHeaders().getFirst("Content-Type"); if(type!=null) builder.header("Content-Type",type);
                    boolean proofPost="POST".equals(exchange.getRequestMethod()) && exchange.getRequestURI().getPath().endsWith("/compensation-proofs");
                    if(proofPost) requests.add(new String(body,StandardCharsets.UTF_8));
                    var response=client.send(builder.method(exchange.getRequestMethod(),HttpRequest.BodyPublishers.ofByteArray(body)).build(),HttpResponse.BodyHandlers.ofByteArray());
                    if(proofPost && requests.size()==1 && response.statusCode()==200 && "COMPENSATED".equals(new ObjectMapper().readTree(response.body()).path("status").asText())) return;
                    response.headers().firstValue("Content-Type").ifPresent(v->exchange.getResponseHeaders().set("Content-Type",v));
                    exchange.sendResponseHeaders(response.statusCode(),response.body().length); exchange.getResponseBody().write(response.body());
                } catch(Exception e) { try { exchange.sendResponseHeaders(502,-1); } catch(Exception ignored) { } }
                finally { exchange.close(); }
            }); server.start();
        }
        int port() { return server.getAddress().getPort(); }
        @Override public void close() { server.stop(0); }
    }
    private static HttpResponse<String> get(String url,String token) throws Exception { return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build().send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).header("Authorization","Bearer "+token).GET().build(),HttpResponse.BodyHandlers.ofString()); }
    private static HttpResponse<String> lateFinancialEvent(int port,boolean unresolved,String eventId) throws Exception {
        String object=unresolved ? "{\"object\":\"dispute\",\"id\":\"dp_fixture\",\"charge\":\"ch_fixture\",\"status\":\"needs_response\",\"livemode\":false,\"amount\":1250,\"currency\":\"usd\"}"
                : "{\"object\":\"charge\",\"id\":\"ch_late\",\"payment_intent\":\"pi_fixture\",\"status\":\"succeeded\",\"livemode\":false,\"amount\":1250,\"currency\":\"usd\"}";
        String body="{\"id\":\""+eventId+"\",\"object\":\"event\",\"type\":\""+(unresolved?"charge.dispute.updated":"charge.succeeded")+"\",\"data\":{\"object\":"+object+"}}";
        long timestamp=java.time.Instant.now().getEpochSecond(); var mac=javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec("whsec_gate11_fixture".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        String signature="t="+timestamp+",v1="+java.util.HexFormat.of().formatHex(mac.doFinal((timestamp+"."+body).getBytes(StandardCharsets.UTF_8)));
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/webhooks/stripe")).timeout(Duration.ofSeconds(30)).header("Content-Type","application/json").header("Stripe-Signature",signature).POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    private static void exec(Connection c,String sql) throws Exception { try(var s=c.createStatement()) { s.execute(sql); } }
    private static long number(Connection c,String sql) throws Exception { try(var s=c.createStatement(); var r=s.executeQuery(sql)) { assertTrue(r.next()); return r.getLong(1); } }
    private static String text(Connection c,String sql) throws Exception { try(var s=c.createStatement(); var r=s.executeQuery(sql)) { assertTrue(r.next()); return r.getString(1); } }
}
