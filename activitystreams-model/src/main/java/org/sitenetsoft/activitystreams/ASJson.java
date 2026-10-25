package org.sitenetsoft.activitystreams;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/** Factory for an {@link ObjectMapper} configured for Activity Streams. */
public final class ASJson {

    private static final ObjectMapper DEFAULT = mapper();

    private ASJson() {
    }

    /** A new mapper using {@link ASTypes#defaults()}. */
    public static ObjectMapper mapper() {
        return mapper(ASTypes.defaults());
    }

    /**
     * A new mapper: Java time and JDK 8 modules, {@link ASModule}, unknown properties tolerated, dates written as
     * ISO-8601 strings ({@code Instant} in UTC, e.g. {@code 2026-10-25T10:00:00Z}).
     */
    public static ObjectMapper mapper(ASTypes types) {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .addModule(new Jdk8Module())
                .addModule(new ASModule(types))
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }

    /** Shared default mapper for internal use (e.g. {@link ASObject#copy()}); must not be reconfigured. */
    static ObjectMapper defaultMapper() {
        return DEFAULT;
    }
}
