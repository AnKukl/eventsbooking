# CLAUDE.md

Spring Boot 4.1.2 · Java 25 · Maven (wrapper) · Web MVC, Validation, Actuator, Data JPA, PostgreSQL.
Базовый пакет: `com.example.eventsbooking`.

## Команды

Всегда используй Maven Wrapper (`./mvnw`, в PowerShell/cmd — `.\mvnw.cmd`), системный Maven не нужен.

| Задача | Команда |
|---|---|
| Сборка + все тесты | `./mvnw verify` |
| Только тесты | `./mvnw test` |
| Один тестовый класс | `./mvnw test -Dtest=ИмяКласса` |
| Один метод | `./mvnw test -Dtest=ИмяКласса#метод` |
| Запуск приложения | `./mvnw spring-boot:run` |
| Сборка jar без тестов | `./mvnw package -DskipTests` |
| Очистка | `./mvnw clean` |

### Windows: ошибка PKIX / certificate_unknown
Если Maven не может скачать зависимости (`PKIX path building failed`), JDK не доверяет сертификату,
подменённому антивирусом/прокси. Использовать хранилище сертификатов Windows:

```
# PowerShell (на сессию)
$env:MAVEN_OPTS = "-Djavax.net.ssl.trustStoreType=Windows-ROOT"
# или постоянно, на уровне пользователя (права администратора не нужны)
setx MAVEN_OPTS "-Djavax.net.ssl.trustStoreType=Windows-ROOT"
```

## База данных

Параметры берутся из переменных окружения (значения по умолчанию в `application.properties`):
`DB_URL` (`jdbc:postgresql://localhost:5432/eventsbooking`), `DB_USERNAME` (`postgres`), `DB_PASSWORD` (`postgres`).
PostgreSQL поднимается через Docker Compose (`compose.yaml`, образ `postgres:17`, БД `eventsbooking`):

```
docker compose up -d        # запустить БД
docker compose ps           # статус (healthy)
docker compose down         # остановить (данные сохраняются в volume pgdata)
docker compose down -v      # остановить и удалить данные
```

## Безопасность (JWT)

Приложение — stateless API на Spring Security: `POST /api/auth/register`, `POST /api/auth/login`, дальше
`Authorization: Bearer <token>`. Роли `USER` и `ORGANIZER`; создавать/менять/удалять мероприятия может только `ORGANIZER`,
`/actuator/health` открыт, всё остальное — только с токеном (deny by default).

Секрет подписи берётся **только** из переменной окружения `JWT_SECRET` (минимум 32 байта), значения по умолчанию нет —
без неё приложение не стартует. Не коммить секрет и не логируй токены/пароли.

```
# PowerShell, на сессию (сгенерировать случайный секрет)
$env:JWT_SECRET = [Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 }) -as [byte[]])
```

Для запуска приложения нужен работающий PostgreSQL. Тесты загрузки контекста идут без БД
(см. `src/test/resources/application.properties`); тесты, которым нужна реальная БД, поднимай через Testcontainers.

## Правила разработки

1. **Тесты перед кодом (TDD).** Сначала пишется падающий тест, запусти его и убедись, что он падает по
   правильной причине. Только потом — минимальная реализация, затем рефакторинг. Не пиши продакшн-код без теста.
2. **Только конструкторная инъекция.** Никаких `@Autowired` на полях и сеттерах. Зависимости — `private final`,
   один конструктор (аннотация `@Autowired` не нужна). Для тестов — тоже конструкторы/параметры, не field injection.
3. **Ошибки API — через `ProblemDetail` (RFC 9457).** Исключения превращаются в `ProblemDetail` в одном
   `@RestControllerAdvice` (наследник `ResponseEntityExceptionHandler`). Не возвращай голые строки,
   собственные DTO ошибок или стектрейсы. Ошибки валидации (`MethodArgumentNotValidException`) — со списком
   полей в расширении `ProblemDetail`. `spring.mvc.problemdetails.enabled=true` уже включено.

## Прочие соглашения

- `spring.jpa.open-in-view=false`; схема БД не генерируется Hibernate (`ddl-auto=validate`) — изменения схемы
  только через миграции.
- Не коммить секреты; конфигурация окружения — через переменные окружения.
- Коммиты небольшие и атомарные; тест и реализация, которую он проверяет, — в одном коммите.
