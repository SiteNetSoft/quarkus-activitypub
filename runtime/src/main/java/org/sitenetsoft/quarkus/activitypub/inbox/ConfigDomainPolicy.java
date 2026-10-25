package org.sitenetsoft.quarkus.activitypub.inbox;

import java.util.List;
import java.util.Locale;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.sitenetsoft.quarkus.activitypub.ActivityPubConfig;

import io.quarkus.arc.DefaultBean;

/**
 * Default {@link DomainPolicy}: blocks the hosts in {@code quarkus.activitypub.blocked-domains}, by suffix
 * ({@code example.com} also blocks {@code sub.example.com}, not {@code badexample.com}), ignoring case.
 */
@DefaultBean
@ApplicationScoped
public class ConfigDomainPolicy implements DomainPolicy {

    @Inject
    ActivityPubConfig config;

    private List<String> blocked;

    @PostConstruct
    void init() {
        blocked = config.blockedDomains().orElse(List.of()).stream().map(String::strip).filter(d -> !d.isEmpty())
                .map(d -> d.toLowerCase(Locale.ROOT)).toList();
    }

    @Override
    public Decision allow(String host) {
        if (host == null) {
            return Decision.ALLOW;
        }
        String h = host.toLowerCase(Locale.ROOT);
        for (String domain : blocked) {
            if (h.equals(domain) || h.endsWith("." + domain)) {
                return Decision.BLOCK;
            }
        }
        return Decision.ALLOW;
    }
}
