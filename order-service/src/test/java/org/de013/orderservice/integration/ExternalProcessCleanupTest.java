package org.de013.orderservice.integration;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExternalProcessCleanupTest {
    public static void main(String[] args) throws Exception { Thread.sleep(60000); }

    @Test void interruptedCleanupTerminatesChildAndRestoresInterrupt() throws Exception {
        Process child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), ExternalProcessCleanupTest.class.getName()).start();
        try {
            Thread.currentThread().interrupt();
            CrossServiceCheckoutIT.closeProcess(child);
            assertTrue(Thread.interrupted());
            assertTrue(child.waitFor(5, TimeUnit.SECONDS));
            assertFalse(child.isAlive());
        } finally {
            Thread.interrupted();
            if (child.isAlive()) child.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
        }
    }
}
