package org.sitenetsoft.quarkus.activitypub;

import org.sitenetsoft.quarkus.activitypub.server.BeanHandler;

import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

@Recorder
public class ActivityPubRecorder {

    /** A route handler that looks up {@code beanClassName} lazily and asks it for {@code variant} (may be null). */
    public Handler<RoutingContext> handler(String beanClassName, String variant) {
        return new BeanHandler(beanClassName, variant);
    }
}
