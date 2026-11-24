# JWT MFA и онлайн-шашки

## Локальный запуск

1. Подготовьте локальный `.env` с `DB_PASSWORD`, `ACCESS_SECRET`, `REFRESH_SECRET` и `ADMIN_PASSWORD_HASH`. Если используете другой логин PostgreSQL, задайте `DB_USERNAME`.
2. Запустите PostgreSQL и Redis.

   ```sh
   docker compose up -d --wait
   ```
3. Запустите приложение из корня проекта.

   ```sh
   DB_URL=jdbc:postgresql://localhost:5432/jwt_demo REDIS_HOST=localhost REDIS_PORT=6380 ./mvnw spring-boot:run
   ```

PostgreSQL и Redis доступны только через loopback. Compose хранит данные в именованных томах. Liquibase применяет миграции при старте backend. Для получения кодов MFA настройте SMTP через переменные `MAIL_*`.

GraphQL HTTP доступен по `/api/v1/graphql`, GraphQL WebSocket по `/api/v1/graphql/ws`. Схема находится в [checkers.graphqls](src/main/resources/graphql/checkers.graphqls).

## GraphiQL

Для запуска с браузерной консолью добавьте `SPRING_PROFILES_ACTIVE=local` к команде запуска backend. Откройте `http://localhost:8080/graphiql` и укажите в панели Headers `{"Authorization": "Bearer <accessToken>"}`.

## Быстрый матч

Кнопка «Играть» вызывает мутацию `quickMatch { id status }`. Если статус `WAITING`, подпишитесь на `gameUpdated(id: ...)` с полученным идентификатором. Как только подключится второй игрок, статус станет `IN_PROGRESS`. Повторный вызов `quickMatch` возвращает текущую заявку или уже начатую партию. Кнопка отмены вызывает `cancelQuickMatch(id: ...)` для ожидающей заявки. Партии по приглашению можно создавать и открывать прежними операциями `createGame` и `joinGame`.
