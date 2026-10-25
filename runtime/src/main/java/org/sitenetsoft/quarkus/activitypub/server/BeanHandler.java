package org.sitenetsoft.quarkus.activitypub.server;

import io.quarkus.arc.Arc;
import io.quarkus.runtime.annotations.RecordableConstructor;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

/**
 * Looks up a {@link Handler} bean lazily on first request, so recorders never touch the container at static init.
 */
public final class BeanHandler implements Handler<RoutingContext> {

    private final String beanClassName;
    private final String variant;
    private volatile Handler<RoutingContext> delegate;

    @RecordableConstructor
    public BeanHandler(String beanClassName, String variant) {
        this.beanClassName = beanClassName;
        this.variant = variant;
    }

    public String getBeanClassName() {
        return beanClassName;
    }

    public String getVariant() {
        return variant;
    }

    @Override
    public void handle(RoutingContext ctx) {
        Handler<RoutingContext> h = delegate;
        if (h == null) {
            try {
                Class<?> type = Thread.currentThread().getContextClassLoader().loadClass(beanClassName);
                Object bean = Arc.container().instance(type).get();
                h = bean instanceof VariantHandler v ? v.variant(variant) : (Handler<RoutingContext>) bean;
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
            delegate = h;
        }
        h.handle(ctx);
    }

    /**
     * A bean that serves several routes from one instance.
     */
    public interface VariantHandler {
        Handler<RoutingContext> variant(String variant);
    }
}
