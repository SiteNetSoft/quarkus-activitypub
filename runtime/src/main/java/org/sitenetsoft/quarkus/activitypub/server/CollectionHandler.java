package org.sitenetsoft.quarkus.activitypub.server;

import java.net.URI;
import java.util.Optional;
import java.util.function.Function;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.sitenetsoft.activitystreams.ObjectOrLink;
import org.sitenetsoft.activitystreams.OrderedCollection;
import org.sitenetsoft.activitystreams.OrderedCollectionPage;
import org.sitenetsoft.quarkus.activitypub.ActivityPubConfig;
import org.sitenetsoft.quarkus.activitypub.ActivityPubConfig.FollowersVisibility;
import org.sitenetsoft.quarkus.activitypub.ActivityPubJson;
import org.sitenetsoft.quarkus.activitypub.ActivityPubUrls;
import org.sitenetsoft.quarkus.activitypub.actor.LocalActor;
import org.sitenetsoft.quarkus.activitypub.store.Page;
import org.sitenetsoft.quarkus.activitypub.store.Stores;

import io.smallrye.mutiny.Uni;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

/**
 * {@code GET} outbox, followers and following. Without {@code page}: an {@code OrderedCollection} with
 * {@code totalItems} and {@code first}; with {@code ?page=N} (N ≥ 1): an {@code OrderedCollectionPage}, newest first.
 * {@code collections.followers-visibility} applies to followers and following: {@code count-only} serves the
 * collection without {@code first} and answers pages with {@code 403}; {@code hidden} answers {@code 404}.
 */
@ApplicationScoped
public class CollectionHandler implements BeanHandler.VariantHandler {

    /** The variants, named as the route variants. */
    enum Kind {
        OUTBOX,
        FOLLOWERS,
        FOLLOWING
    }

    @Inject
    ActivityPubConfig config;

    @Inject
    ActivityPubUrls urls;

    @Inject
    Stores stores;

    @Inject
    ActivityPubJson json;

    @Inject
    FetchAuthorization authorization;

    @Override
    public Handler<RoutingContext> variant(String variant) {
        Kind kind = Kind.valueOf(variant);
        return ctx -> handle(ctx, kind);
    }

    void handle(RoutingContext ctx, Kind kind) {
        if (!ActivityPubRouting.readOnly(ctx)) {
            return;
        }
        FollowersVisibility visibility = kind == Kind.OUTBOX ? FollowersVisibility.PUBLIC
                : config.collections().followersVisibility();
        if (visibility == FollowersVisibility.HIDDEN) {
            ActivityPubRouting.status(ctx, 404);
            return;
        }
        String pageParam = ctx.request().getParam("page");
        int page = pageParam == null ? 0 : parsePage(pageParam);
        if (page < 0) {
            ActivityPubRouting.status(ctx, 400);
            return;
        }
        if (page > 0 && visibility == FollowersVisibility.COUNT_ONLY) {
            ActivityPubRouting.status(ctx, 403);
            return;
        }
        String username = ctx.pathParam("username");
        authorization.authorize(ctx, false, signer -> ActivityPubRouting.respond(ctx,
                stores.reactiveActors().findByUsername(username).flatMap(actor -> render(kind, actor, page, visibility)),
                body -> body.ifPresentOrElse(b -> ActivityPubRouting.json(ctx, 200, b),
                        () -> ActivityPubRouting.status(ctx, 404))));
    }

    /** The page number, or -1 when it is not an integer ≥ 1. */
    static int parsePage(String value) {
        try {
            int n = Integer.parseInt(value.strip());
            return n >= 1 ? n : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private Uni<Optional<byte[]>> render(Kind kind, Optional<LocalActor> found, int page, FollowersVisibility visibility) {
        if (found.isEmpty()) {
            return Uni.createFrom().item(Optional.empty());
        }
        LocalActor actor = found.get();
        URI id = collectionId(kind, actor.username());
        if (page == 0) {
            return count(kind, actor.id()).map(total -> {
                OrderedCollection collection = new OrderedCollection();
                collection.id(id);
                collection.totalItems(total);
                if (visibility == FollowersVisibility.PUBLIC) {
                    collection.first(ObjectOrLink.of(pageUri(id, 1)));
                }
                return Optional.of(json.write(collection));
            });
        }
        int size = config.collections().pageSize();
        return page(kind, actor.id(), page, size).map(items -> {
            OrderedCollectionPage result = new OrderedCollectionPage();
            result.id(pageUri(id, page));
            result.partOf(ObjectOrLink.of(id));
            result.orderedItems(items.items().toArray(ObjectOrLink[]::new));
            if (items.hasNext()) {
                result.next(ObjectOrLink.of(pageUri(id, page + 1)));
            }
            if (page > 1) {
                result.prev(ObjectOrLink.of(pageUri(id, page - 1)));
            }
            return Optional.of(json.write(result));
        });
    }

    private URI collectionId(Kind kind, String username) {
        return switch (kind) {
            case OUTBOX -> urls.outbox(username);
            case FOLLOWERS -> urls.followers(username);
            case FOLLOWING -> urls.following(username);
        };
    }

    private Uni<Long> count(Kind kind, URI actor) {
        return switch (kind) {
            case OUTBOX -> stores.reactiveObjects().outboxCount(actor);
            case FOLLOWERS -> stores.reactiveFollowers().followerCount(actor);
            case FOLLOWING -> stores.reactiveFollowers().followingCount(actor);
        };
    }

    private Uni<Page<ObjectOrLink>> page(Kind kind, URI actor, int page, int size) {
        return switch (kind) {
            case OUTBOX -> stores.reactiveObjects().outbox(actor, page, size).map(p -> map(p, ObjectOrLink::of));
            case FOLLOWERS -> stores.reactiveFollowers().followers(actor, page, size).map(p -> map(p, ObjectOrLink::of));
            case FOLLOWING -> stores.reactiveFollowers().following(actor, page, size).map(p -> map(p, ObjectOrLink::of));
        };
    }

    private static <T> Page<ObjectOrLink> map(Page<T> page, Function<T, ObjectOrLink> f) {
        return new Page<>(page.items().stream().map(f).toList(), page.page(), page.size(), page.total());
    }

    private static URI pageUri(URI collection, int page) {
        return URI.create(collection + "?page=" + page);
    }
}
