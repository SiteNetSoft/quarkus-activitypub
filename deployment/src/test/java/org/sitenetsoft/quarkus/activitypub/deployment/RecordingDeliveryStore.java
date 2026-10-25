package org.sitenetsoft.quarkus.activitypub.deployment;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;

import org.sitenetsoft.quarkus.activitypub.store.Delivery;
import org.sitenetsoft.quarkus.activitypub.store.DeliveryStore;
import org.sitenetsoft.quarkus.activitypub.store.memory.InMemoryDeliveryStore;

/** The in-memory delivery store, letting tests look deliveries up by activity (the SPI has no such query). */
@ApplicationScoped
@Typed({ DeliveryStore.class, RecordingDeliveryStore.class })
public class RecordingDeliveryStore extends InMemoryDeliveryStore {

    private final Map<String, Delivery> latest = new ConcurrentHashMap<>();

    @Override
    public void save(List<Delivery> deliveries) {
        super.save(deliveries);
        deliveries.forEach(d -> latest.put(d.id(), d));
    }

    @Override
    public void update(Delivery delivery) {
        super.update(delivery);
        latest.put(delivery.id(), delivery);
    }

    /** The current state of every delivery of {@code activityId}, by inbox. */
    public List<Delivery> forActivity(URI activityId) {
        return latest.values().stream().filter(d -> d.activityId().equals(activityId))
                .sorted(Comparator.comparing((Delivery d) -> d.inbox().toString())).toList();
    }

    /** The only delivery of {@code activityId}. */
    public Delivery only(URI activityId) {
        List<Delivery> all = forActivity(activityId);
        if (all.size() != 1) {
            throw new AssertionError("Expected one delivery of " + activityId + ", got " + all);
        }
        return all.get(0);
    }
}
