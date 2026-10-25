package org.sitenetsoft.quarkus.nodeinfo;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;

/**
 * Version-independent NodeInfo data. {@link NodeInfoDocuments} turns it into a 2.0 or 2.1 document.
 *
 * @param software the server software (2.0 documents drop {@code repository} and {@code homepage})
 * @param protocols protocol names, e.g. {@code activitypub}
 * @param services third-party services
 * @param openRegistrations whether self-registration is open
 * @param usage usage statistics
 * @param metadata free-form metadata
 */
public record NodeInfo(Software software, Set<String> protocols, Services services, boolean openRegistrations, Usage usage,
        Map<String, JsonNode> metadata) {

    public NodeInfo {
        Objects.requireNonNull(software, "software");
        protocols = copy(protocols);
        services = services == null ? Services.NONE : services;
        usage = usage == null ? Usage.NONE : usage;
        metadata = metadata == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    public static Builder builder(Software software) {
        return new Builder(software);
    }

    public Builder toBuilder() {
        Builder b = new Builder(software);
        b.protocols.addAll(protocols);
        b.inbound.addAll(services.inbound());
        b.outbound.addAll(services.outbound());
        b.openRegistrations = openRegistrations;
        b.usage = usage;
        b.metadata.putAll(metadata);
        return b;
    }

    /**
     * Merges a contribution into this NodeInfo: protocols and services are unioned, metadata entries are put (the
     * contribution's value replaces an existing key), usage numbers the contribution sets replace this one's, and
     * registrations are open when either says so. The software stays this NodeInfo's.
     */
    public NodeInfo merge(NodeInfo contribution) {
        if (contribution == null) {
            return this;
        }
        Builder b = toBuilder();
        b.protocols.addAll(contribution.protocols);
        b.inbound.addAll(contribution.services.inbound());
        b.outbound.addAll(contribution.services.outbound());
        b.openRegistrations = openRegistrations || contribution.openRegistrations;
        b.usage = usage.merge(contribution.usage);
        b.metadata.putAll(contribution.metadata);
        return b.build();
    }

    private static Set<String> copy(Set<String> s) {
        return s == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(s));
    }

    public record Software(String name, String version, Optional<URI> repository, Optional<URI> homepage) {
        public Software {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(version, "version");
            repository = repository == null ? Optional.empty() : repository;
            homepage = homepage == null ? Optional.empty() : homepage;
        }

        public static Software of(String name, String version) {
            return new Software(name, version, Optional.empty(), Optional.empty());
        }
    }

    public record Services(Set<String> inbound, Set<String> outbound) {
        static final Services NONE = new Services(Set.of(), Set.of());

        public Services {
            inbound = copy(inbound);
            outbound = copy(outbound);
        }
    }

    public record Usage(Users users, Optional<Long> localPosts, Optional<Long> localComments) {
        static final Usage NONE = new Usage(Users.NONE, Optional.empty(), Optional.empty());

        public Usage {
            users = users == null ? Users.NONE : users;
            localPosts = localPosts == null ? Optional.empty() : localPosts;
            localComments = localComments == null ? Optional.empty() : localComments;
        }

        Usage merge(Usage c) {
            return new Usage(users.merge(c.users), or(c.localPosts, localPosts), or(c.localComments, localComments));
        }
    }

    public record Users(Optional<Long> total, Optional<Long> activeMonth, Optional<Long> activeHalfyear) {
        static final Users NONE = new Users(Optional.empty(), Optional.empty(), Optional.empty());

        public Users {
            total = total == null ? Optional.empty() : total;
            activeMonth = activeMonth == null ? Optional.empty() : activeMonth;
            activeHalfyear = activeHalfyear == null ? Optional.empty() : activeHalfyear;
        }

        Users merge(Users c) {
            return new Users(or(c.total, total), or(c.activeMonth, activeMonth), or(c.activeHalfyear, activeHalfyear));
        }
    }

    private static <T> Optional<T> or(Optional<T> preferred, Optional<T> fallback) {
        return preferred.isPresent() ? preferred : fallback;
    }

    public static final class Builder {
        private Software software;
        private final Set<String> protocols = new LinkedHashSet<>();
        private final Set<String> inbound = new LinkedHashSet<>();
        private final Set<String> outbound = new LinkedHashSet<>();
        private boolean openRegistrations;
        private Usage usage = Usage.NONE;
        private final Map<String, JsonNode> metadata = new LinkedHashMap<>();

        private Builder(Software software) {
            this.software = Objects.requireNonNull(software, "software");
        }

        public Builder withSoftware(Software software) {
            this.software = Objects.requireNonNull(software, "software");
            return this;
        }

        public Builder withProtocol(String protocol) {
            protocols.add(protocol);
            return this;
        }

        public Builder withInboundService(String service) {
            inbound.add(service);
            return this;
        }

        public Builder withOutboundService(String service) {
            outbound.add(service);
            return this;
        }

        public Builder withOpenRegistrations(boolean open) {
            this.openRegistrations = open;
            return this;
        }

        public Builder withUsersTotal(long total) {
            Users u = usage.users();
            return withUsers(new Users(Optional.of(total), u.activeMonth(), u.activeHalfyear()));
        }

        public Builder withUsersActiveMonth(long activeMonth) {
            Users u = usage.users();
            return withUsers(new Users(u.total(), Optional.of(activeMonth), u.activeHalfyear()));
        }

        public Builder withUsersActiveHalfyear(long activeHalfyear) {
            Users u = usage.users();
            return withUsers(new Users(u.total(), u.activeMonth(), Optional.of(activeHalfyear)));
        }

        public Builder withLocalPosts(long localPosts) {
            usage = new Usage(usage.users(), Optional.of(localPosts), usage.localComments());
            return this;
        }

        public Builder withLocalComments(long localComments) {
            usage = new Usage(usage.users(), usage.localPosts(), Optional.of(localComments));
            return this;
        }

        public Builder withMetadata(String key, JsonNode value) {
            metadata.put(key, value);
            return this;
        }

        public Builder withMetadata(String key, String value) {
            return withMetadata(key, TextNode.valueOf(value));
        }

        private Builder withUsers(Users users) {
            usage = new Usage(users, usage.localPosts(), usage.localComments());
            return this;
        }

        public NodeInfo build() {
            return new NodeInfo(software, protocols, new Services(inbound, outbound), openRegistrations, usage, metadata);
        }
    }
}
