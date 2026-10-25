package org.sitenetsoft.quarkus.activitypub.inbox;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Qualifier;

/**
 * Qualifies the CDI events fired for activities received through the inbox:
 * {@code void onFollow(@Observes @Inbound Follow follow)}. The event type is the activity's concrete class, so
 * observers of a supertype ({@code Activity}, {@code ASObject}) see every activity. {@link InboundActivityContext}
 * tells who sent it and to whom.
 */
@Qualifier
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER, METHOD, TYPE })
public @interface Inbound {

    final class Literal extends AnnotationLiteral<Inbound> implements Inbound {

        public static final Literal INSTANCE = new Literal();

        private static final long serialVersionUID = 1L;

        private Literal() {
        }
    }
}
