# eventsbooking

Spring Boot 4 / Java 25 / PostgreSQL / Kafka. Команды и правила разработки — в [CLAUDE.md](CLAUDE.md),
архитектурные решения — в [docs/decisions.md](docs/decisions.md).

## Запуск

```
docker compose up -d            # PostgreSQL и Kafka
$env:JWT_SECRET = "..."         # минимум 32 байта, см. CLAUDE.md
.\mvnw.cmd spring-boot:run
```

Kafka: `KAFKA_BOOTSTRAP_SERVERS` (по умолчанию `localhost:9092`).

## Kafka: события бронирования

После успешной брони публикуется `BookingCreated` в топик `booking-events`. Consumer (группа `notifications`)
сохраняет запись в таблицу `notifications`; повторное сообщение с тем же `eventId` дубля не создаёт.

## Следующий шаг: dead letter topic

Сейчас сообщение, которое не удалось обработать после 3 повторов (или нечитаемое), логируется и пропускается —
уведомление теряется. Следующий шаг — dead letter topic `booking-events.DLT`:

1. Заменить пропуск на `DeadLetterPublishingRecoverer` в `DefaultErrorHandler`: проблемная запись уходит в
   `booking-events.DLT` вместе с причиной ошибки в заголовках.
2. Алерт на ненулевой лаг/рост `booking-events.DLT`.
3. Инструмент или эндпоинт для повторной отправки записей из DLT после исправления причины.
4. Для потери события между коммитом брони и отправкой — transactional outbox (см. decisions.md, запись 006).
