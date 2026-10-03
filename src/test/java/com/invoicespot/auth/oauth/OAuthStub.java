package com.invoicespot.auth.oauth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

// A stand-in for Google, so sign-in runs end to end in tests without it. It
// signs ID tokens with a key of its own, published as a JWK set, for
// whoever the next sign-in is set to be, and keeps what each token request
// sent, to check the code and PKCE. One per test run, on a free port.
public final class OAuthStub {

    public static final String GOOGLE_CLIENT_ID = "google-test-client";
    public static final String GOOGLE_CLIENT_SECRET = "google-test-secret";

    // `hostedDomain` is the hd claim: the Google Workspace domain someone is
    // at, or null for anyone else.
    public record GoogleUser(
            String sub, String email, boolean emailVerified, String givenName, String familyName, String hostedDomain) {

        public GoogleUser(String sub, String email, boolean emailVerified, String givenName, String familyName) {
            this(sub, email, emailVerified, givenName, familyName, null);
        }
    }

    private static final OAuthStub INSTANCE = new OAuthStub();

    private final HttpServer server;
    private final RSAKey key;
    private final List<Map<String, String>> tokenRequests = new CopyOnWriteArrayList<>();
    private volatile GoogleUser nextGoogle;
    private volatile String nextNonce;

    private OAuthStub() {
        try {
            key = new RSAKeyGenerator(2048).keyID("stub").generate();
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        } catch (JOSEException exception) {
            throw new IllegalStateException(exception);
        }
        server.createContext("/google/token", this::googleToken);
        server.createContext("/google/userinfo", exchange -> json(exchange, 200, googleClaims(nextGoogle)));
        server.createContext("/google/jwks", exchange -> json(exchange, 200, new JWKSet(key.toPublicJWK()).toString()));
        server.start();
    }

    public static OAuthStub get() {
        return INSTANCE;
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public String googleIssuer() {
        return url() + "/google";
    }

    // Who the next Google sign-in is, with the nonce its authorization
    // request carried.
    public void nextGoogle(GoogleUser user, String nonce) {
        this.nextGoogle = user;
        this.nextNonce = nonce;
    }

    public Map<String, String> lastTokenRequest() {
        return tokenRequests.get(tokenRequests.size() - 1);
    }

    private void googleToken(HttpExchange exchange) throws IOException {
        Map<String, String> form = form(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        form.put("authorization", exchange.getRequestHeaders().getFirst("Authorization"));
        tokenRequests.add(form);
        GoogleUser user = nextGoogle;
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(googleIssuer())
                .subject(user.sub())
                .audience(GOOGLE_CLIENT_ID)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .claim("nonce", nextNonce);
        googleClaimsOf(user).forEach(claims::claim);
        SignedJWT idToken = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims.build());
        try {
            idToken.sign(new RSASSASigner(key));
        } catch (JOSEException exception) {
            throw new IllegalStateException(exception);
        }
        json(exchange, 200, """
                {"access_token": "stub-access-token", "token_type": "Bearer", "expires_in": 3600,
                 "scope": "openid profile email", "id_token": "%s"}
                """.formatted(idToken.serialize()));
    }

    private static Map<String, Object> googleClaimsOf(GoogleUser user) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", user.sub());
        if (user.email() != null) {
            claims.put("email", user.email());
        }
        claims.put("email_verified", user.emailVerified());
        if (user.givenName() != null) {
            claims.put("given_name", user.givenName());
        }
        if (user.familyName() != null) {
            claims.put("family_name", user.familyName());
        }
        if (user.hostedDomain() != null) {
            claims.put("hd", user.hostedDomain());
        }
        return claims;
    }

    private static String googleClaims(GoogleUser user) {
        StringBuilder json = new StringBuilder("{");
        googleClaimsOf(user).forEach((name, value) -> json.append(json.length() > 1 ? "," : "")
                .append('"').append(name).append("\":")
                .append(value instanceof Boolean ? value : "\"" + value + "\""));
        return json.append('}').toString();
    }

    private static Map<String, String> form(String body) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String pair : body.split("&")) {
            int at = pair.indexOf('=');
            if (at > 0) {
                values.put(
                        URLDecoder.decode(pair.substring(0, at), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(at + 1), StandardCharsets.UTF_8));
            }
        }
        return values;
    }

    private static void json(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
