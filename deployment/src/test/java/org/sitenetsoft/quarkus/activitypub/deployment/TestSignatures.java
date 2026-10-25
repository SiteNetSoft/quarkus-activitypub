package org.sitenetsoft.quarkus.activitypub.deployment;

import static io.restassured.RestAssured.given;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

import org.sitenetsoft.httpsignatures.Algorithm;
import org.sitenetsoft.httpsignatures.Headers;
import org.sitenetsoft.httpsignatures.HttpMessageView;
import org.sitenetsoft.httpsignatures.HttpSigner;
import org.sitenetsoft.httpsignatures.KeyResolver;
import org.sitenetsoft.httpsignatures.SignatureSpec;
import org.sitenetsoft.httpsignatures.SigningKey;
import org.sitenetsoft.httpsignatures.VerificationKey;
import org.sitenetsoft.quarkus.activitypub.actor.ActorKeyPair;
import org.sitenetsoft.quarkus.activitypub.actor.ActorKeys;

import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;

/**
 * Remote test signers: {@code carol} and {@code dave} on {@code remote.example} share one RSA key pair, resolved by
 * {@link RemoteKeys} (add it to the test archive).
 */
final class TestSignatures {

    static final String CAROL = "https://remote.example/users/carol";
    static final String CAROL_KEY = CAROL + "#main-key";
    static final String DAVE_KEY = "https://remote.example/users/dave#main-key";
    static final ActorKeyPair REMOTE_KEYS = ActorKeys.generateRsa2048();

    private TestSignatures() {
    }

    @ApplicationScoped
    public static class RemoteKeys implements KeyResolver {
        @Override
        public Optional<VerificationKey> resolve(String keyId) {
            if (CAROL_KEY.equals(keyId) || DAVE_KEY.equals(keyId)) {
                return Optional.of(new VerificationKey(keyId, Optional.of(Algorithm.RSA_SHA256),
                        ActorKeys.publicKey(REMOTE_KEYS)));
            }
            return Optional.empty();
        }
    }

    /** An ActivityPub GET of {@code path} signed with Cavage {@code rsa-sha256} by {@code keyId}. */
    static RequestSpecification signedGet(String keyId, String path) {
        URI uri = URI.create("http://localhost:" + RestAssured.port + path);
        String host = uri.getHost() + ":" + uri.getPort();
        HttpMessageView msg = HttpMessageView.of("GET", uri, Headers.of(Map.of("Host", List.of(host))), null);
        var signed = new HttpSigner().sign(msg, SignatureSpec.cavage(keyId, Algorithm.RSA_SHA256, Instant.now(), false),
                new SigningKey(keyId, Algorithm.RSA_SHA256, ActorKeys.privateKey(REMOTE_KEYS)));
        RequestSpecification spec = given().accept(TestActors.AP).header("Host", host);
        signed.headers().forEach(spec::header);
        return spec;
    }
}
