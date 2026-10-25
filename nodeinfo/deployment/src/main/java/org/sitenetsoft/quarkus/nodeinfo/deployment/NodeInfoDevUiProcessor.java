package org.sitenetsoft.quarkus.nodeinfo.deployment;

import org.sitenetsoft.quarkus.nodeinfo.devui.NodeInfoJsonRpcService;

import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.devui.spi.JsonRPCProvidersBuildItem;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;

public class NodeInfoDevUiProcessor {

    @BuildStep(onlyIf = IsDevelopment.class)
    CardPageBuildItem card() {
        CardPageBuildItem card = new CardPageBuildItem();
        // the page link's label shows the advertised software name and version on the card
        card.addPage(Page.webComponentPageBuilder()
                .title("Preview")
                .icon("font-awesome-solid:file-code")
                .dynamicLabelJsonRPCMethodName("software")
                .componentLink("qwc-nodeinfo-preview.js"));
        return card;
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    JsonRPCProvidersBuildItem jsonRpc() {
        return new JsonRPCProvidersBuildItem(NodeInfoJsonRpcService.class);
    }
}
