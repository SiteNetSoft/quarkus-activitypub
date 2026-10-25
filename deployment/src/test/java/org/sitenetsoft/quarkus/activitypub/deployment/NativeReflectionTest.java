package org.sitenetsoft.quarkus.activitypub.deployment;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sitenetsoft.activitystreams.ASTypes;
import org.sitenetsoft.activitystreams.ObjectOrLink;
import org.sitenetsoft.activitystreams.json.ObjectOrLinkDeserializer;
import org.sitenetsoft.quarkus.activitypub.store.Delivery;

import io.quarkus.builder.BuildContext;
import io.quarkus.builder.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.test.QuarkusUnitTest;

/** The classes Jackson needs by reflection are registered for the native image (native itself is CI's job). */
public class NativeReflectionTest {

    private static final String KEY = "activitypub.test.reflective";

    @RegisterExtension
    static final QuarkusUnitTest config = LoopbackTests.unitTest()
            .addBuildChainCustomizer(chain -> chain.addBuildStep(new BuildStep() {
                @Override
                public void execute(BuildContext context) {
                    String names = context.consumeMulti(ReflectiveClassBuildItem.class).stream()
                            .filter(item -> item.isMethods() && item.isFields())
                            .flatMap(item -> item.getClassNames().stream()).collect(Collectors.joining(","));
                    System.setProperty(KEY, names);
                    context.produce(new FeatureBuildItem("reflection-probe"));
                }
            }).consumes(ReflectiveClassBuildItem.class).produces(FeatureBuildItem.class).build());

    @Test
    void jacksonTypesAreRegistered() {
        Set<String> registered = Set.of(System.getProperty(KEY, "").split(","));
        for (Class<?> type : ASTypes.defaults().classes()) {
            assertTrue(registered.contains(type.getName()), type.getName());
        }
        for (Class<?> type : List.of(org.sitenetsoft.activitystreams.Actor.class, ObjectOrLink.class, ObjectOrLink.Ref.class, ObjectOrLink.Embedded.class,
                ObjectOrLinkDeserializer.class, Delivery.class,
                org.sitenetsoft.activitystreams.Endpoints.class, org.sitenetsoft.activitystreams.PublicKey.class)) {
            assertTrue(registered.contains(type.getName()), type.getName());
        }
        assertTrue(registered.contains("org.sitenetsoft.quarkus.nodeinfo.NodeInfo$Software"), "NodeInfo records");
    }
}
