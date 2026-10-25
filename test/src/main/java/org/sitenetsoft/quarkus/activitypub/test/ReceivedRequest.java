package org.sitenetsoft.quarkus.activitypub.test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * A request the {@link FakeFediverseServer} received. Header names are case-insensitive. {@code verified} is true when
 * the request carried an HTTP signature that verified against the key the fake fetched from {@code keyId};
 * {@code keyId} is null when the request was unsigned or its signature unreadable.
 */
public record ReceivedRequest(String method, String path, Map<String, List<String>> headers, byte[] body,
        boolean verified, String keyId) {

    public ReceivedRequest {
        TreeMap<String, List<String>> ci = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.forEach((k, v) -> ci.put(k, List.copyOf(v)));
        headers = java.util.Collections.unmodifiableMap(ci);
        body = body == null ? new byte[0] : body;
    }

    public Optional<String> header(String name) {
        List<String> values = headers.get(name);
        return values == null || values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    public String bodyAsString() {
        return new String(body, StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return "ReceivedRequest[" + method + " " + path + ", verified=" + verified + ", keyId=" + keyId + ", body="
                + body.length + " bytes]";
    }
}
