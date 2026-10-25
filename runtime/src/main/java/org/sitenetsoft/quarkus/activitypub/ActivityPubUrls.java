package org.sitenetsoft.quarkus.activitypub;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkus.runtime.Startup;

/**
 * Builds and parses the URLs of local actors, collections, objects and activities from {@code quarkus.activitypub.base-url}
 * and the {@code quarkus.activitypub.paths.*} templates. Templates are validated at startup.
 */
@ApplicationScoped
@Startup
public class ActivityPubUrls {

    private static final String PREFIX = "quarkus.activitypub.paths.";
    private static final String USERNAME = "{username}";
    private static final String ID = "{id}";

    @Inject
    ActivityPubConfig config;

    @Inject
    ActivityPubBuildTimeConfig buildConfig;

    private URI baseUrl;
    private String base;
    private String basePath;
    private Template actor;
    private Template inbox;
    private Template outbox;
    private Template followers;
    private Template following;
    private Template objects;
    private Template activities;
    private String sharedInbox;

    @PostConstruct
    void init() {
        ActivityPubBuildTimeConfig.Paths paths = buildConfig.paths();
        actor = Template.of(PREFIX + "actor", paths.actor(), USERNAME);
        inbox = Template.of(PREFIX + "inbox", paths.inbox(), USERNAME);
        outbox = Template.of(PREFIX + "outbox", paths.outbox(), USERNAME);
        followers = Template.of(PREFIX + "followers", paths.followers(), USERNAME);
        following = Template.of(PREFIX + "following", paths.following(), USERNAME);
        objects = Template.of(PREFIX + "objects", paths.objects(), ID);
        activities = Template.of(PREFIX + "activities", paths.activities(), ID);
        sharedInbox = paths.sharedInbox();
        if (sharedInbox.contains(USERNAME) || sharedInbox.contains(ID)) {
            throw new IllegalStateException(PREFIX + "shared-inbox must not contain a placeholder: '" + sharedInbox + "'");
        }
        baseUrl = config.baseUrl();
        if (baseUrl.getHost() == null) {
            throw new IllegalStateException("quarkus.activitypub.base-url must be an absolute URL with a host: '" + baseUrl + "'");
        }
        base = stripTrailingSlash(baseUrl.toString());
        basePath = stripTrailingSlash(baseUrl.getRawPath() == null ? "" : baseUrl.getRawPath());
    }

    /** {@code quarkus.activitypub.base-url}. */
    public URI baseUrl() {
        return baseUrl;
    }

    public URI actor(String username) {
        return actor.build(base, username);
    }

    public URI inbox(String username) {
        return inbox.build(base, username);
    }

    public URI outbox(String username) {
        return outbox.build(base, username);
    }

    public URI followers(String username) {
        return followers.build(base, username);
    }

    public URI following(String username) {
        return following.build(base, username);
    }

    public URI sharedInbox() {
        return URI.create(base + sharedInbox);
    }

    public URI object(String id) {
        return objects.build(base, id);
    }

    public URI activity(String id) {
        return activities.build(base, id);
    }

    /** The actor's main key id: the actor URL with fragment {@code #main-key}. */
    public URI keyId(String username) {
        return URI.create(actor(username) + "#main-key");
    }

    /**
     * The local id a request path stands for: {@code base-url} (its path kept, trailing slash dropped) followed by
     * {@code rawPath}, so {@code https://h/social} and {@code /posts/1} give {@code https://h/social/posts/1}. Empty
     * unless {@code rawPath} starts with exactly one {@code /} (a {@code //host/...} path would otherwise name another
     * origin) and the result is a valid URI.
     */
    public Optional<URI> idForPath(String rawPath) {
        if (rawPath == null || !rawPath.startsWith("/") || rawPath.startsWith("//")) {
            return Optional.empty();
        }
        try {
            return Optional.of(new URI(base + rawPath));
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }

    /** The username of a local actor URL, empty when the URL is not a local actor URL. */
    public Optional<String> usernameOf(URI actorId) {
        return parse(actor, actorId);
    }

    /** The id of a local object URL, empty otherwise. */
    public Optional<String> objectIdOf(URI uri) {
        return parse(objects, uri);
    }

    /** The id of a local activity URL, empty otherwise. */
    public Optional<String> activityIdOf(URI uri) {
        return parse(activities, uri);
    }

    /** True when the URI has the same host (case-insensitive) and effective port as {@code base-url}. */
    public boolean isLocal(URI uri) {
        return uri != null && uri.getHost() != null && uri.getHost().equalsIgnoreCase(baseUrl.getHost())
                && port(uri) == port(baseUrl);
    }

    /** The host of {@code base-url}, with {@code :port} when the URL names a non-default port. */
    public String host() {
        int explicit = baseUrl.getPort();
        return explicit == -1 || explicit == defaultPort(baseUrl) ? baseUrl.getHost() : baseUrl.getHost() + ":" + explicit;
    }

    private Optional<String> parse(Template template, URI uri) {
        if (!isLocal(uri) || uri.getRawPath() == null) {
            return Optional.empty();
        }
        String path = uri.getRawPath();
        if (!path.startsWith(basePath)) {
            return Optional.empty();
        }
        return template.parse(path.substring(basePath.length()));
    }

    private static int port(URI uri) {
        return uri.getPort() != -1 ? uri.getPort() : defaultPort(uri);
    }

    private static int defaultPort(URI uri) {
        return "http".equalsIgnoreCase(uri.getScheme()) ? 80 : 443;
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    static String decode(String value) {
        // encode() never emits '+', so a literal '+' in a path means '+', not a space
        return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    private record Template(String prefix, String suffix) {

        static Template of(String property, String template, String placeholder) {
            int first = template.indexOf(placeholder);
            if (first < 0 || template.indexOf(placeholder, first + 1) >= 0) {
                throw new IllegalStateException(property + " must contain " + placeholder + " exactly once: '" + template + "'");
            }
            return new Template(template.substring(0, first), template.substring(first + placeholder.length()));
        }

        URI build(String base, String value) {
            return URI.create(base + prefix + encode(value) + suffix);
        }

        Optional<String> parse(String path) {
            if (path.length() <= prefix.length() + suffix.length() || !path.startsWith(prefix) || !path.endsWith(suffix)) {
                return Optional.empty();
            }
            String raw = path.substring(prefix.length(), path.length() - suffix.length());
            if (raw.contains("/")) {
                return Optional.empty();
            }
            try {
                return Optional.of(decode(raw));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }
    }
}
