package org.sitenetsoft.quarkus.activitypub.deployment;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import org.sitenetsoft.quarkus.activitypub.outbound.DeliveryEvents.DeliveryCompleted;
import org.sitenetsoft.quarkus.activitypub.outbound.DeliveryEvents.DeliveryFailed;

/** Records the delivery events. */
@ApplicationScoped
public class DeliveryEventCollector {

    private final List<DeliveryCompleted> completed = new CopyOnWriteArrayList<>();
    private final List<DeliveryFailed> failed = new CopyOnWriteArrayList<>();

    void onCompleted(@Observes DeliveryCompleted event) {
        completed.add(event);
    }

    void onFailed(@Observes DeliveryFailed event) {
        failed.add(event);
    }

    public List<DeliveryCompleted> completed(URI activityId) {
        return completed.stream().filter(e -> e.delivery().activityId().equals(activityId)).toList();
    }

    public List<DeliveryFailed> failed(URI activityId) {
        return failed.stream().filter(e -> e.delivery().activityId().equals(activityId)).toList();
    }
}
