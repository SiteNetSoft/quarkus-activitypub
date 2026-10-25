package org.sitenetsoft.quarkus.activitypub.hibernate.deployment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;

import io.quarkus.test.QuarkusUnitTest;

/**
 * Tests that talk to a {@code FakeFediverseServer} over loopback: the app serves on a port reserved up front so its {@code base-url}
 * is known at config time (the fake fetches our actor to verify our signatures), and safe-fetch is relaxed for
 * {@code http://127.0.0.1}. The Hibernate stores run on an in-memory H2 database whose schema Hibernate creates.
 */
final class LoopbackTests {

    static final int PORT = reservedPort("quarkus");
    static final String BASE = "http://127.0.0.1:" + PORT;

    private LoopbackTests() {
    }

    static QuarkusUnitTest unitTest(Class<?>... classes) {
        return new QuarkusUnitTest()
                .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(LoopbackTests.class)
                        .addClasses(classes))
                .overrideConfigKey("quarkus.http.test-port", String.valueOf(PORT))
                .overrideConfigKey("quarkus.activitypub.base-url", BASE)
                .overrideConfigKey("quarkus.safe-fetch.https-only", "false")
                .overrideConfigKey("quarkus.safe-fetch.allow-private-addresses", "true")
                .overrideConfigKey("quarkus.datasource.db-kind", "h2")
                .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:ap;DB_CLOSE_DELAY=-1")
                .overrideConfigKey("quarkus.hibernate-orm.schema-management.strategy", "drop-and-create");
    }

    /**
     * A free port, chosen once per {@code name} and kept in a system property: QuarkusUnitTest loads the test class in
     * two class loaders, and both static initialisers must agree on the port.
     */
    static int reservedPort(String name) {
        String key = "activitypub.test.port." + name;
        synchronized (System.getProperties()) {
            String existing = System.getProperty(key);
            if (existing != null) {
                return Integer.parseInt(existing);
            }
            try (ServerSocket socket = new ServerSocket(0)) {
                int port = socket.getLocalPort();
                System.setProperty(key, String.valueOf(port));
                return port;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
