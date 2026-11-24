package com.elyashevich.jwt_2fa_template.api.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import com.elyashevich.jwt_2fa_template.TestcontainersConfiguration;
import com.elyashevich.jwt_2fa_template.dto.auth.JwtType;
import com.elyashevich.jwt_2fa_template.entity.UserProfile;
import com.elyashevich.jwt_2fa_template.repository.UserProfileRepository;
import com.elyashevich.jwt_2fa_template.service.JwtService;
import com.elyashevich.jwt_2fa_template.service.UserSessionService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.time.Instant;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

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
    private JwtService jwtService;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private UserSessionService userSessionService;

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

                var stale = query(client, white, move, variables);
                assertThat(json.readTree(stale.body()).path("errors").get(0).path("extensions")
                        .path("code").asString()).isEqualTo("STALE_GAME_VERSION");
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
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/graphql"))
                .timeout(Duration.ofSeconds(10))
                .header("Origin", "http://localhost:3000")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of(
                        "query", query, "variables", variables))));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
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
