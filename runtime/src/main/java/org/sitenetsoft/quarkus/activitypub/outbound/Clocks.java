package org.sitenetsoft.quarkus.activitypub.outbound;

import java.time.Clock;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import io.quarkus.arc.DefaultBean;

/**
 * The {@link Clock} delivery scheduling reads (attempt times, backoff, host pauses, backlog age): UTC system time
 * unless the application provides another {@code Clock} bean.
 */
@ApplicationScoped
public class Clocks {

    @Produces
    @DefaultBean
    @ApplicationScoped
    Clock clock() {
        return Clock.systemUTC();
    }
}
