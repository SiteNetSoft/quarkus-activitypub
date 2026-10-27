package org.acme.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

/** Without {@code demo.token} (as in the prod profile unless it is set) {@code POST /demo/post} is disabled: 404. */
@QuarkusTest
@TestProfile(DemoDisabledTest.NoToken.class)
class DemoDisabledTest {

    public static class NoToken implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("demo.token", "", "%test.demo.token", "");
        }
    }

    @TestHTTPResource("/demo/post?user=alice&text=hi")
    URL post;

    @Test
    void postIsNotFoundWithoutConfiguredToken() throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        URI uri = post.toURI();
        assertEquals(404, http.send(HttpRequest.newBuilder(uri).POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(404, http.send(HttpRequest.newBuilder(uri).header("Authorization", "Bearer ")
                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString())
                .statusCode());
    }
}
