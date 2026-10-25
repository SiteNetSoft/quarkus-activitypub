package org.sitenetsoft.quarkus.activitypub.actor;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * An actor this server owns.
 *
 * @param username   local username ({@code preferredUsername}), matched exactly
 * @param type       Activity Streams actor type, {@code Person} by default
 * @param id         actor URL
 * @param extra      additional properties merged into the actor document
 */
public record LocalActor(String username, String type, URI id, Optional<String> name, Optional<String> summary,
        Optional<URI> url, Optional<URI> icon, Optional<URI> image, boolean manuallyApprovesFollowers,
        boolean discoverable, Instant published, Map<String, JsonNode> extra) {

    public LocalActor {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(published, "published");
        extra = Map.copyOf(extra);
    }

    public static Builder builder(String username) {
        return new Builder(username);
    }

    public static final class Builder {

        private final String username;
        private String type = "Person";
        private URI id;
        private String name;
        private String summary;
        private URI url;
        private URI icon;
        private URI image;
        private boolean manuallyApprovesFollowers;
        private boolean discoverable = true;
        private Instant published = Instant.now();
        private final Map<String, JsonNode> extra = new LinkedHashMap<>();

        private Builder(String username) {
            this.username = Objects.requireNonNull(username, "username");
        }

        public String username() {
            return username;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder id(URI id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder summary(String summary) {
            this.summary = summary;
            return this;
        }

        public Builder url(URI url) {
            this.url = url;
            return this;
        }

        public Builder icon(URI icon) {
            this.icon = icon;
            return this;
        }

        public Builder image(URI image) {
            this.image = image;
            return this;
        }

        public Builder manuallyApprovesFollowers(boolean manuallyApprovesFollowers) {
            this.manuallyApprovesFollowers = manuallyApprovesFollowers;
            return this;
        }

        public Builder discoverable(boolean discoverable) {
            this.discoverable = discoverable;
            return this;
        }

        public Builder published(Instant published) {
            this.published = published;
            return this;
        }

        public Builder extra(String property, JsonNode value) {
            extra.put(property, value);
            return this;
        }

        /** @throws IllegalStateException when no {@link #id(URI)} was set */
        public LocalActor build() {
            if (id == null) {
                throw new IllegalStateException("LocalActor '" + username + "' needs an id");
            }
            return new LocalActor(username, type, id, Optional.ofNullable(name), Optional.ofNullable(summary),
                    Optional.ofNullable(url), Optional.ofNullable(icon), Optional.ofNullable(image),
                    manuallyApprovesFollowers, discoverable, published, extra);
        }
    }
}
