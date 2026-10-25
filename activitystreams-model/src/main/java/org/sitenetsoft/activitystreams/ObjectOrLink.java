package org.sitenetsoft.activitystreams;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/**
 * Value of an object-valued property: either a bare URI reference or an embedded object (or link).
 * <p>
 * The record components are named {@code href} and {@code value} because a component named {@code uri}/{@code object}
 * would generate accessors clashing with the {@link Optional}-returning interface methods.
 */
public sealed interface ObjectOrLink permits ObjectOrLink.Ref, ObjectOrLink.Embedded {

    /** A bare URI (the JSON value was a string). */
    record Ref(URI href) implements ObjectOrLink {
        public Ref {
            Objects.requireNonNull(href, "href");
        }

        @Override
        public Optional<URI> uri() {
            return Optional.of(href);
        }

        @Override
        public Optional<ASObject> object() {
            return Optional.empty();
        }
    }

    /** An embedded object (the JSON value was an object). */
    record Embedded(ASObject value) implements ObjectOrLink {
        public Embedded {
            Objects.requireNonNull(value, "value");
        }

        @Override
        public Optional<URI> uri() {
            return Optional.ofNullable(value.id());
        }

        @Override
        public Optional<ASObject> object() {
            return Optional.of(value);
        }
    }

    /** The reference URI, or the embedded object's id (empty for an embedded object without id). */
    Optional<URI> uri();

    /** The embedded object, empty for a bare reference. */
    Optional<ASObject> object();

    static ObjectOrLink of(URI uri) {
        return new Ref(uri);
    }

    static ObjectOrLink of(ASObject object) {
        return new Embedded(object);
    }

    static ObjectOrLink of(String uri) {
        return new Ref(URI.create(uri));
    }
}
