package com.elyashevich.jwt_2fa_template.api.graphql;

import static com.elyashevich.jwt_2fa_template.api.graphql.CheckersGameUpdates.CURRENT_VERSION;
import static com.elyashevich.jwt_2fa_template.api.graphql.GraphQlAuthentication.SOCKET_TOKEN;
import static com.elyashevich.jwt_2fa_template.api.graphql.GraphQlAuthentication.USER_ID;

import com.elyashevich.jwt_2fa_template.dto.checkers.CheckersGameSnapshot;
import com.elyashevich.jwt_2fa_template.entity.CheckersGameStatus;
import com.elyashevich.jwt_2fa_template.entity.CheckersSide;
import com.elyashevich.jwt_2fa_template.service.CheckersGameService;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import jakarta.persistence.EntityNotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.GraphQlExceptionHandler;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Controller
@RequiredArgsConstructor
class CheckersGraphQlController {

    private final CheckersGameService checkersGameService;
    private final CheckersGameUpdates checkersGameUpdates;
    private final GraphQlAuthentication graphQlAuthentication;

    @QueryMapping
    GameView game(@Argument UUID id, @ContextValue(USER_ID) long userId) {
        return GameView.from(checkersGameService.get(id, userId));
    }

    @MutationMapping
    GameView createGame(@ContextValue(USER_ID) long userId) {
        return GameView.from(checkersGameService.create(userId));
    }

    @MutationMapping
    GameView quickMatch(@ContextValue(USER_ID) long userId) {
        return GameView.from(checkersGameService.quickMatch(userId));
    }

    @MutationMapping
    GameView cancelQuickMatch(@Argument UUID id, @ContextValue(USER_ID) long userId) {
        return GameView.from(checkersGameService.cancelQuickMatch(id, userId));
    }

    @MutationMapping
    GameView joinGame(@Argument UUID id, @ContextValue(USER_ID) long userId) {
        return GameView.from(checkersGameService.join(id, userId));
    }

    @MutationMapping
    GameView makeMove(@Argument UUID id, @Argument String expectedVersion, @Argument List<Integer> route, @ContextValue(USER_ID) long userId) {
        return GameView.from(checkersGameService.makeMove(id, userId, Long.parseLong(expectedVersion), route));
    }

    @SubscriptionMapping
    Flux<GameView> gameUpdated(@Argument UUID id, @ContextValue(USER_ID) long userId, @ContextValue(name = SOCKET_TOKEN, required = false) String token) {
        if (token == null) {
            throw new AccessDeniedException("A WebSocket connection is required");
        }
        checkersGameService.get(id, userId);
        Duration remaining = Duration.between(Instant.now(), graphQlAuthentication.expiresAt(token));
        if (remaining.isNegative() || remaining.isZero()) {
            return Flux.empty();
        }

        Flux<Long> versions = checkersGameUpdates.forGame(id)
                .scan((latest, next) -> next == CURRENT_VERSION ? next : Math.max(latest, next))
                .distinctUntilChanged()
                .onBackpressureLatest();
        return Flux.merge(1, versions, Mono.just(CURRENT_VERSION))
                .concatMap(update -> Mono.fromCallable(() -> {
                    graphQlAuthentication.authenticate(token);
                    return checkersGameUpdates.load(id, update, userId);
                }).subscribeOn(Schedulers.boundedElastic()), 0)
                .scan((latest, next) -> latest.version() >= next.version() ? latest : next)
                .distinctUntilChanged(CheckersGameSnapshot::version)
                .map(GameView::from)
                .take(remaining);
    }

    @GraphQlExceptionHandler
    GraphQLError forbidden(AccessDeniedException ex, DataFetchingEnvironment environment) {
        return GraphqlErrorBuilder.newError(environment).errorType(ErrorType.FORBIDDEN).message(ex.getMessage()).build();
    }

    @GraphQlExceptionHandler
    GraphQLError notFound(EntityNotFoundException ex, DataFetchingEnvironment environment) {
        return GraphqlErrorBuilder.newError(environment).errorType(ErrorType.NOT_FOUND).message(ex.getMessage()).build();
    }

    @GraphQlExceptionHandler
    GraphQLError staleVersion(ObjectOptimisticLockingFailureException ex, DataFetchingEnvironment environment) {
        return GraphqlErrorBuilder.newError(environment).errorType(ErrorType.BAD_REQUEST)
                .message("Game version has changed")
                .extensions(Map.of("code", "STALE_GAME_VERSION"))
                .build();
    }

    @GraphQlExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    GraphQLError invalidAction(RuntimeException ex, DataFetchingEnvironment environment) {
        String message = ex instanceof NumberFormatException ? "Invalid game version" : "Invalid checkers action";
        return GraphqlErrorBuilder.newError(environment).errorType(ErrorType.BAD_REQUEST).message(message).build();
    }

    record GameView(String id, String whitePlayerId, String blackPlayerId, String board, CheckersSide turn, CheckersGameStatus status, CheckersSide winner, String version) {
        static GameView from(CheckersGameSnapshot game) {
            Long blackPlayerId = game.blackPlayerId();
            return new GameView(game.id().toString(), Long.toString(game.whitePlayerId()),
                    blackPlayerId == null ? null : blackPlayerId.toString(), game.board(), game.turn(),
                    game.status(), game.winner(), Long.toString(game.version()));
        }
    }
}
