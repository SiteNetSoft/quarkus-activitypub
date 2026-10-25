package org.sitenetsoft.quarkus.activitypub;

import jakarta.inject.Singleton;

import org.sitenetsoft.activitystreams.ASModule;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.jackson.ObjectMapperCustomizer;

/**
 * Registers {@link ASModule} on the Quarkus-managed {@link ObjectMapper}.
 */
@Singleton
public class ActivityPubObjectMapperCustomizer implements ObjectMapperCustomizer {

    @Override
    public void customize(ObjectMapper objectMapper) {
        objectMapper.registerModule(new ASModule());
    }
}
