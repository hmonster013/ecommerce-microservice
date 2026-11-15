package org.de013.orderservice;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.de013.orderservice.client.InventoryReservationClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;

import static org.junit.jupiter.api.Assertions.*;

class InventoryMissingRegistrationTest {
    @Test void missingRegistrationFailsBeforeNetworkCall() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> { calls.incrementAndGet(); exchange.sendResponseHeaders(200, -1); exchange.close(); });
        server.start();
        try {
            SpringApplication app = new SpringApplication(InventoryFeignSecurityTest.App.class);
            app.setWebApplicationType(WebApplicationType.SERVLET);
            String url = "http://127.0.0.1:" + server.getAddress().getPort();
            try (var context = app.run("--server.port=0", "--spring.cloud.openfeign.client.config.inventoryReservation.url=" + url,
                    "--spring.cloud.openfeign.client.config.orderCart.url=" + url)) {
                assertThrows(Exception.class, () -> context.getBean(InventoryReservationClient.class).lookup(UUID.randomUUID()));
                assertEquals(0, calls.get());
            }
        } finally { server.stop(0); }
    }
}
