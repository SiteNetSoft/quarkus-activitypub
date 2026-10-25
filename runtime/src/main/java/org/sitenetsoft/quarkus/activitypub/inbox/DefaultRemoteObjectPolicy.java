package org.sitenetsoft.quarkus.activitypub.inbox;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.sitenetsoft.activitystreams.ASObject;
import org.sitenetsoft.activitystreams.ObjectOrLink;
import org.sitenetsoft.quarkus.activitypub.ActivityPubUrls;
import org.sitenetsoft.quarkus.activitypub.store.Stores;

import io.quarkus.arc.DefaultBean;

/**
 * Default {@link RemoteObjectPolicy}: keeps an object addressed to anything local, replying to a local object, or
 * already held (so updates of kept objects apply).
 */
@DefaultBean
@ApplicationScoped
public class DefaultRemoteObjectPolicy implements RemoteObjectPolicy {

    @Inject
    ActivityPubUrls urls;

    @Inject
    Stores stores;

    @Override
    public Keep decide(ASObject object, InboundActivityContext ctx) {
        boolean addressedHere = object.audienceUris().stream().anyMatch(urls::isLocal);
        boolean replyHere = object.inReplyTo().stream().map(ObjectOrLink::uri)
                .anyMatch(uri -> uri.filter(urls::isLocal).isPresent());
        boolean held = object.id() != null && stores.objects().find(object.id()).isPresent();
        return addressedHere || replyHere || held ? Keep.KEEP : Keep.IGNORE;
    }
}
