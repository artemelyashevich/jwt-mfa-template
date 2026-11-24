package com.elyashevich.jwt_2fa_template.api.graphql;

import static com.elyashevich.jwt_2fa_template.dto.cache.CacheType.CHECKERS_GAME_SNAPSHOT;
import static org.assertj.core.api.Assertions.assertThat;

import com.elyashevich.jwt_2fa_template.TestcontainersConfiguration;
import com.elyashevich.jwt_2fa_template.dto.auth.JwtType;
import com.elyashevich.jwt_2fa_template.dto.checkers.CheckersGameSnapshot;
import com.elyashevich.jwt_2fa_template.entity.UserProfile;
import com.elyashevich.jwt_2fa_template.repository.UserProfileRepository;
import com.elyashevich.jwt_2fa_template.service.CacheService;
import com.elyashevich.jwt_2fa_template.service.JwtService;
import com.elyashevich.jwt_2fa_template.service.UserSessionService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "app.auth.access-secret=test-access-secret-test-access-secret-0123456789",
    "app.auth.refresh-secret=test-refresh-secret-test-refresh-secret-0123456789",
    "ADMIN_PASSWORD_HASH=test-admin-password-hash",
    "app.cors.allowed-origins=http://localhost:3000"
})
class CheckersGraphQlIntegrationTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @LocalServerPort
    private int port;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private UserSessionService userSessionService;

    @Test
    void servesLocalGraphiQlWithoutAuthenticatingThePage() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/graphiql"))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(307);
            URI destination = request.uri().resolve(response.headers().firstValue("Location").orElseThrow());
            assertThat(destination.getQuery()).contains("path=/api/v1/graphql", "wsPath=/api/v1/graphql/ws");

            var page = client.send(HttpRequest.newBuilder(destination).timeout(Duration.ofSeconds(10)).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(page.statusCode()).isEqualTo(200);
            assertThat(page.body()).contains("GraphiQL");
            assertThat(query(client, null, "query { __typename }", Map.of()).statusCode()).isEqualTo(401);
        }
    }

    @Test
    void distributesCommittedChangesToAuthenticatedParticipants() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            String white = accessToken();
            String black = accessToken();
            String outsider = accessToken();

            String create = "mutation { createGame { id version } }";
            assertThat(query(client, null, create, Map.of()).statusCode()).isEqualTo(401);
            var created = data(query(client, white, create, Map.of())).path("createGame");
            String gameId = created.path("id").asString();
            assertThat(gameId).isNotBlank();

            var denied = query(client, outsider, "query($id: ID!) { game(id: $id) { id } }", Map.of("id", gameId));
            assertThat(json.readTree(denied.body()).path("errors").get(0).path("extensions")
                    .path("classification").asString()).isEqualTo("FORBIDDEN");

            var outsideListener = new Messages();
            var outsideSocket = connect(client, outsideListener);
            try {
                initialize(outsideSocket, outsideListener, outsider);
                subscribe(outsideSocket, gameId);
                var rejection = outsideListener.next(json);
                assertThat(rejection.path("type").asString()).isEqualTo("error");
                assertThat(rejection.path("payload").get(0).path("extensions")
                        .path("classification").asString()).isEqualTo("FORBIDDEN");
            } finally {
                outsideSocket.abort();
            }

            var listener = new Messages();
            var socket = connect(client, listener);
            try {
                initialize(socket, listener, white);
                subscribe(socket, gameId);
                assertThat(update(listener).path("status").asString()).isEqualTo("WAITING");

                String join = "mutation($id: ID!) { joinGame(id: $id) { version } }";
                var joined = data(query(client, black, join, Map.of("id", gameId))).path("joinGame");
                assertThat(update(listener).path("status").asString()).isEqualTo("IN_PROGRESS");

                String move = """
                        mutation($id: ID!, $version: ID!) {
                            makeMove(id: $id, expectedVersion: $version, route: [20, 16]) { version }
                        }
                        """;
                var variables = Map.of("id", gameId, "version", joined.path("version").asString());
                var moved = data(query(client, white, move, variables)).path("makeMove");
                var update = update(listener);
                assertThat(update.path("turn").asString()).isEqualTo("BLACK");
                assertThat(update.path("board").asString().charAt(16)).isEqualTo('w');
                assertThat(update.path("version").asString()).isEqualTo(moved.path("version").asString());

                String version = moved.path("version").asString();
                var cached = cacheService.<CheckersGameSnapshot>get(CHECKERS_GAME_SNAPSHOT, gameId + ":" + version);
                assertThat(cached).hasValueSatisfying(snapshot -> {
                    assertThat(snapshot.version()).isEqualTo(Long.parseLong(version));
                    assertThat(snapshot.board()).isEqualTo(update.path("board").asString());
                });

                var stale = query(client, white, move, variables);
                assertThat(json.readTree(stale.body()).path("errors").get(0).path("extensions")
                        .path("code").asString()).isEqualTo("STALE_GAME_VERSION");

                String blackMove = move.replace("[20, 16]", "[8, 12]");
                var blackVariables = Map.of("id", gameId, "version", version);
                var first = client.sendAsync(request(black, blackMove, blackVariables), HttpResponse.BodyHandlers.ofString());
                var second = client.sendAsync(request(black, blackMove, blackVariables), HttpResponse.BodyHandlers.ofString());

                var responses = List.of(json.readTree(first.get(10, TimeUnit.SECONDS).body()),
                        json.readTree(second.get(10, TimeUnit.SECONDS).body()));
                assertThat(responses).filteredOn(response -> response.path("data").has("makeMove")).hasSize(1);
                assertThat(responses).filteredOn(response -> "STALE_GAME_VERSION".equals(
                        response.path("errors").path(0).path("extensions").path("code").asString())).hasSize(1);
                assertThat(update(listener).path("turn").asString()).isEqualTo("WHITE");
            } finally {
                socket.abort();
            }
        }
    }

    @Test
    void pairsConcurrentRequestsInOneGameAndKeepsRetriesIdempotent() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            String firstPlayer = accessToken();
            String secondPlayer = accessToken();
            String quickMatch = "mutation { quickMatch { id status whitePlayerId blackPlayerId } }";
            String invite = "mutation { createGame { id } }";
            String inviteId = data(query(client, firstPlayer, invite, Map.of())).path("createGame").path("id").asString();

            var first = client.sendAsync(request(firstPlayer, quickMatch, Map.of()), HttpResponse.BodyHandlers.ofString());
            var second = client.sendAsync(request(secondPlayer, quickMatch, Map.of()), HttpResponse.BodyHandlers.ofString());

            var firstResult = data(first.get(10, TimeUnit.SECONDS)).path("quickMatch");
            var secondResult = data(second.get(10, TimeUnit.SECONDS)).path("quickMatch");
            assertThat(firstResult.path("id").asString()).isNotEqualTo(inviteId);
            assertThat(firstResult.path("id").asString()).isEqualTo(secondResult.path("id").asString());
            assertThat(List.of(firstResult.path("status").asString(), secondResult.path("status").asString()))
                    .containsExactlyInAnyOrder("WAITING", "IN_PROGRESS");

            var retry = data(query(client, firstPlayer, quickMatch, Map.of())).path("quickMatch");
            assertThat(retry.path("id").asString()).isEqualTo(firstResult.path("id").asString());
            assertThat(retry.path("status").asString()).isEqualTo("IN_PROGRESS");
        }
    }

    @Test
    void cancelsWaitingSearchAndNotifiesSubscriber() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            String player = accessToken();
            String outsider = accessToken();
            String quickMatch = "mutation { quickMatch { id status } }";
            var waiting = data(query(client, player, quickMatch, Map.of())).path("quickMatch");
            String gameId = waiting.path("id").asString();
            assertThat(waiting.path("status").asString()).isEqualTo("WAITING");

            var parameters = Map.of("id", gameId);
            String join = "mutation($id: ID!) { joinGame(id: $id) { status } }";
            var rejectedJoin = query(client, outsider, join, parameters);
            assertThat(json.readTree(rejectedJoin.body()).path("errors").path(0).path("extensions")
                    .path("classification").asString()).isEqualTo("FORBIDDEN");

            var listener = new Messages();
            var socket = connect(client, listener);
            try {
                initialize(socket, listener, player);
                subscribe(socket, gameId);
                assertThat(update(listener).path("status").asString()).isEqualTo("WAITING");

                String cancel = "mutation($id: ID!) { cancelQuickMatch(id: $id) { status } }";
                var rejectedCancel = query(client, outsider, cancel, parameters);
                assertThat(json.readTree(rejectedCancel.body()).path("errors").path(0).path("extensions")
                        .path("classification").asString()).isEqualTo("FORBIDDEN");

                var cancelled = data(query(client, player, cancel, parameters)).path("cancelQuickMatch");
                assertThat(cancelled.path("status").asString()).isEqualTo("CANCELLED");
                assertThat(update(listener).path("status").asString()).isEqualTo("CANCELLED");

                var nextSearch = data(query(client, player, quickMatch, Map.of())).path("quickMatch");
                assertThat(nextSearch.path("status").asString()).isEqualTo("WAITING");
                assertThat(nextSearch.path("id").asString()).isNotEqualTo(gameId);
                data(query(client, player, cancel, Map.of("id", nextSearch.path("id").asString())));
            } finally {
                socket.abort();
            }
        }
    }

    @Test
    void rejectsWebSocketInitializationWithoutAccessToken() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var listener = new Messages();
            var socket = connect(client, listener);
            try {
                send(socket, Map.of("type", "connection_init"));
                assertThat(listener.closeCodes.poll(10, TimeUnit.SECONDS)).isEqualTo(4401);
            } finally {
                socket.abort();
            }
        }
    }

    private String accessToken() {
        var user = userProfileRepository.save(UserProfile.builder()
                .email(UUID.randomUUID() + "@example.invalid")
                .name("Checkers player")
                .passwordHash("unused-in-this-test")
                .emailVerifiedAt(Instant.now())
                .build());
        String token = jwtService.generate(user, JwtType.ACCESS);
        userSessionService.create(token, user);
        return token;
    }

    private HttpResponse<String> query(HttpClient client, String token, String query, Map<String, String> variables)
            throws Exception {
        return client.send(request(token, query, variables), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest request(String token, String query, Map<String, String> variables) {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/graphql"))
                .timeout(Duration.ofSeconds(10))
                .header("Origin", "http://localhost:3000")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of(
                        "query", query, "variables", variables))));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return request.build();
    }

    private JsonNode data(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(200);
        var body = json.readTree(response.body());
        assertThat(body.has("errors")).withFailMessage(response.body()).isFalse();
        return body.path("data");
    }

    private WebSocket connect(HttpClient client, WebSocket.Listener listener) throws Exception {
        return client.newWebSocketBuilder().subprotocols("graphql-transport-ws")
                .header("Origin", "http://localhost:3000")
                .connectTimeout(Duration.ofSeconds(10))
                .buildAsync(URI.create("ws://localhost:" + port + "/api/v1/graphql/ws"), listener)
                .get(10, TimeUnit.SECONDS);
    }

    private void initialize(WebSocket socket, Messages listener, String token) throws Exception {
        send(socket, Map.of("type", "connection_init", "payload", Map.of("Authorization", "Bearer " + token)));
        assertThat(listener.next(json).path("type").asString()).isEqualTo("connection_ack");
    }

    private void subscribe(WebSocket socket, String gameId) throws Exception {
        send(socket, Map.of("id", "game", "type", "subscribe", "payload", Map.of(
                "query", "subscription($id: ID!) { gameUpdated(id: $id) { status turn board version } }",
                "variables", Map.of("id", gameId))));
    }

    private void send(WebSocket socket, Map<String, ?> message) throws Exception {
        socket.sendText(json.writeValueAsString(message), true).get(10, TimeUnit.SECONDS);
    }

    private JsonNode update(Messages listener) throws InterruptedException {
        var message = listener.next(json);
        assertThat(message.path("type").asString()).withFailMessage(message.toString()).isEqualTo("next");
        return message.path("payload").path("data").path("gameUpdated");
    }

    private static class Messages implements WebSocket.Listener {

        private final LinkedBlockingQueue<String> messages = new LinkedBlockingQueue<>();
        private final LinkedBlockingQueue<Integer> closeCodes = new LinkedBlockingQueue<>();
        private final StringBuilder partialMessage = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            partialMessage.append(data);
            if (last) {
                messages.add(partialMessage.toString());
                partialMessage.setLength(0);
            }
            socket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket socket, int statusCode, String reason) {
            closeCodes.add(statusCode);
            return null;
        }

        JsonNode next(JsonMapper json) throws InterruptedException {
            String message = messages.poll(10, TimeUnit.SECONDS);
            assertThat(message).as("WebSocket message").isNotNull();
            return json.readTree(message);
        }
    }
}
