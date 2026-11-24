package org.de013.shoppingcart;

import java.util.Arrays;

/** Loaded by the packaged application's PropertiesLauncher, without the test classpath. */
public final class ArtifactConfigLauncher {
    public static void main(String[] args) throws Exception {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Class<?> spring = loader.loadClass("org.springframework.boot.SpringApplication");
        Object context = spring.getMethod("run", Class.class, String[].class)
                .invoke(null, loader.loadClass(args[0]), Arrays.copyOfRange(args, 1, args.length));
        try {
            Object environment = context.getClass().getMethod("getEnvironment").invoke(context);
            Class<?> resolver = loader.loadClass("org.springframework.core.env.PropertyResolver");
            for (String[] property : new String[][] {
                    {"spring.flyway.enabled", "true"},
                    {"spring.flyway.baseline-on-migrate", "false"},
                    {"spring.jpa.hibernate.ddl-auto", "validate"}}) {
                Object value = resolver.getMethod("getProperty", String.class).invoke(environment, property[0]);
                if (!property[1].equals(value)) throw new IllegalStateException("Unexpected effective property: " + property[0]);
                Object sources = environment.getClass().getMethod("getPropertySources").invoke(environment);
                boolean remote = false;
                for (Object source : (Iterable<?>) sources) {
                    Class<?> type = loader.loadClass("org.springframework.core.env.PropertySource");
                    String name = (String) type.getMethod("getName").invoke(source);
                    Object configured = type.getMethod("getProperty", String.class).invoke(source, property[0]);
                    if (name.contains("configserver:") && property[1].equals(String.valueOf(configured))) remote = true;
                }
                if (!remote) throw new IllegalStateException("Missing Config Server source for " + property[0]);
            }
            if (args[0].contains("notificationservice")) {
                for (String name : new String[] {"org.de013.notificationservice.service.PaymentEmailWorkers",
                        "org.de013.notificationservice.config.PaymentEmailKafkaConfig"}) {
                    String[] beans = (String[]) context.getClass().getMethod("getBeanNamesForType", Class.class)
                            .invoke(context, loader.loadClass(name));
                    if (beans.length != 0) throw new IllegalStateException("Unexpected payment email component: " + name);
                }
            }
            Class<?> webContext = loader.loadClass("org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext");
            Object server = webContext.getMethod("getWebServer").invoke(context);
            int port = (int) loader.loadClass("org.springframework.boot.web.server.WebServer").getMethod("getPort").invoke(server);
            System.out.println("TEST_READY " + port);
            System.out.flush();
            while (System.in.read() != -1) { }
        } finally {
            context.getClass().getMethod("close").invoke(context);
        }
    }
}
