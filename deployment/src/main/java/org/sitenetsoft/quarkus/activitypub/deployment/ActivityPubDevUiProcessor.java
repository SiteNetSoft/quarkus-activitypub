package org.sitenetsoft.quarkus.activitypub.deployment;

import org.sitenetsoft.quarkus.activitypub.ActivityPubBuildTimeConfig;
import org.sitenetsoft.quarkus.activitypub.devui.ActivityPubJsonRpcService;

import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.devui.spi.JsonRPCProvidersBuildItem;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;

/**
 * The ActivityPub Dev UI card (base URL, instance actor, actor count, queue counts, paused hosts) and its pages, in dev
 * mode when {@code quarkus.activitypub.enabled} and {@code quarkus.activitypub.devui.enabled}.
 */
public class ActivityPubDevUiProcessor {

    @BuildStep(onlyIf = IsDevelopment.class)
    void devUi(ActivityPubBuildTimeConfig config, BuildProducer<CardPageBuildItem> cards,
            BuildProducer<JsonRPCProvidersBuildItem> jsonRpc) {
        if (!config.enabled() || !config.devui().enabled()) {
            return;
        }
        CardPageBuildItem card = new CardPageBuildItem();
        card.setCustomCard("qwc-activitypub-card.js");
        card.addPage(Page.webComponentPageBuilder()
                .title("Actors")
                .icon("font-awesome-solid:users")
                .componentLink("qwc-activitypub-actors.js"));
        card.addPage(Page.webComponentPageBuilder()
                .title("Inbox log")
                .icon("font-awesome-solid:inbox")
                .componentLink("qwc-activitypub-inbox.js"));
        card.addPage(Page.webComponentPageBuilder()
                .title("Fetch")
                .icon("font-awesome-solid:download")
                .componentLink("qwc-activitypub-fetch.js"));
        card.addPage(Page.webComponentPageBuilder()
                .title("Deliveries")
                .icon("font-awesome-solid:paper-plane")
                .componentLink("qwc-activitypub-deliveries.js"));
        cards.produce(card);
        jsonRpc.produce(new JsonRPCProvidersBuildItem(ActivityPubJsonRpcService.class));
    }
}
