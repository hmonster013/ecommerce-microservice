package org.de013.orderservice.integration;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

/** Loaded into each packaged application without exposing the order test classpath. */
public final class GenericExternalApplicationLauncher {
    public static void main(String[] args) throws Exception {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        String stripeBase = System.getProperty("test.stripe.base");
        if (stripeBase != null) {
            loader.loadClass("com.stripe.Stripe").getMethod("overrideApiBase", String.class).invoke(null, stripeBase);
        }
        Class<?> source = loader.loadClass(args[0]);
        Class<?> spring = loader.loadClass("org.springframework.boot.SpringApplication");
        Object context = spring.getMethod("run", Class.class, String[].class)
                .invoke(null, source, Arrays.copyOfRange(args, 1, args.length));
        Class<?> webContext = loader.loadClass("org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext");
        Object server = webContext.getMethod("getWebServer").invoke(context);
        int port = (int) loader.loadClass("org.springframework.boot.web.server.WebServer")
                .getMethod("getPort").invoke(server);
        System.out.println("TEST_READY " + port);
        System.out.flush();
        try (BufferedReader input = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while ((line = input.readLine()) != null) {
                String[] command = line.split(" ", 2);
                try {
                    if ("RECONCILE".equals(command[1])) {
                        Class<?> job = loader.loadClass("org.de013.orderservice.service.impl.ReconciliationJob");
                        Object bean = context.getClass().getMethod("getBean", Class.class).invoke(context, job);
                        var run = job.getDeclaredMethod("run");
                        run.setAccessible(true);
                        run.invoke(bean);
                    } else {
                        long id = Long.parseLong(command[1]);
                        Class<?> recovery = loader.loadClass("org.de013.orderservice.service.impl.CheckoutExpiryRecovery");
                        Object bean = context.getClass().getMethod("getBean", Class.class).invoke(context, recovery);
                        recovery.getMethod("process", long.class).invoke(bean, id);
                    }
                    System.out.println("TEST_RESULT " + command[0] + " OK");
                } catch (Exception e) {
                    Throwable cause = e instanceof InvocationTargetException ? e.getCause() : e;
                    System.out.println("TEST_RESULT " + command[0] + " ERROR " + cause.getClass().getName());
                }
                System.out.flush();
            }
        } finally {
            context.getClass().getMethod("close").invoke(context);
        }
    }
}
