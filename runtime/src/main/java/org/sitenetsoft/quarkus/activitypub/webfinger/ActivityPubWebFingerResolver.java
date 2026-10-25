package org.sitenetsoft.quarkus.activitypub.webfinger;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.sitenetsoft.quarkus.activitypub.ActivityPubMediaTypes;
import org.sitenetsoft.quarkus.activitypub.ActivityPubUrls;
import org.sitenetsoft.quarkus.activitypub.actor.LocalActor;
import org.sitenetsoft.quarkus.activitypub.store.Stores;
import org.sitenetsoft.quarkus.webfinger.AcctUri;
import org.sitenetsoft.quarkus.webfinger.JsonResourceDescriptor;
import org.sitenetsoft.quarkus.webfinger.Link;
import org.sitenetsoft.quarkus.webfinger.ReactiveWebFingerResolver;
import org.sitenetsoft.quarkus.webfinger.WebFingerRequest;

import io.smallrye.mutiny.Uni;

/**
 * Answers WebFinger for local actors: {@code acct:{username}@{base-url host}} (the user part is percent-decoded by
 * {@link AcctUri} and matched exactly) and the actor URI itself. The subject is always the {@code acct:} URI, with the
 * actor id (and its profile page) as aliases, a {@code self} link to the actor document and a
 * {@code http://webfinger.net/rel/profile-page} link when the actor has a {@code url}.
 */
@ApplicationScoped
public class ActivityPubWebFingerResolver implements ReactiveWebFingerResolver {

    public static final String PROFILE_PAGE = "http://webfinger.net/rel/profile-page";

    @Inject
    ActivityPubUrls urls;

    @Inject
    Stores stores;

    @Override
    public Uni<Optional<JsonResourceDescriptor>> resolve(WebFingerRequest request) {
        Uni<Optional<LocalActor>> lookup;
        Optional<AcctUri> account = request.account();
        URI resource = request.resource();
        if (account.isPresent()) {
            if (!account.get().host().equalsIgnoreCase(urls.host())) {
                return Uni.createFrom().item(Optional.empty());
            }
            lookup = stores.reactiveActors().findByUsername(account.get().user());
        } else if (resource != null && resource.isAbsolute() && urls.isLocal(resource)) {
            lookup = stores.reactiveActors().findById(resource);
        } else {
            return Uni.createFrom().item(Optional.empty());
        }
        return lookup.map(actor -> actor.map(this::descriptor));
    }

    private JsonResourceDescriptor descriptor(LocalActor actor) {
        URI subject = new AcctUri(actor.username(), urls.host().toLowerCase(Locale.ROOT)).toUri();
        JsonResourceDescriptor jrd = JsonResourceDescriptor.of(subject).withAlias(actor.id());
        if (actor.url().isPresent()) {
            jrd = jrd.withAlias(actor.url().get());
        }
        jrd = jrd.withLink(Link.of("self", ActivityPubMediaTypes.ACTIVITY_JSON, actor.id()));
        if (actor.url().isPresent()) {
            jrd = jrd.withLink(Link.of(PROFILE_PAGE, "text/html", actor.url().get()));
        }
        return jrd;
    }
}
