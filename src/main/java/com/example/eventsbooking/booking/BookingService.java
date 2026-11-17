package com.example.eventsbooking.booking;

import com.example.eventsbooking.event.Event;
import com.example.eventsbooking.event.EventNotFoundException;
import com.example.eventsbooking.event.EventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * Бронирование места на мероприятие и отмена брони.
 *
 * <h2>Почему оптимистическая блокировка (@Version на Event)</h2>
 * <ul>
 *   <li>Транзакция бронирования короткая (прочитать событие, вставить бронь, обновить счётчик), и
 *       конфликт возникает только когда <em>два человека одновременно берут места одного и того же
 *       события</em>. Для большинства событий это редкость, поэтому дешевле обнаружить конфликт при
 *       записи, чем заставлять каждого ждать на блокировке строки.</li>
 *   <li>Оптимистическая блокировка не держит блокировок БД между чтением и записью и не создаёт
 *       взаимоблокировок. Проигравшая транзакция получает {@link OptimisticLockingFailureException}
 *       и просто повторяется: при повторе она либо займёт оставшееся место, либо увидит, что мест
 *       нет, и вернёт 409.</li>
 *   <li>Версия защищает именно инвариант «занято &lt;= capacity»: и бронь, и отмена меняют
 *       {@code Event.bookedCount}, а значит и версию, поэтому две транзакции, прочитавшие одно и то же
 *       состояние, не смогут обе зафиксироваться.</li>
 *   <li>Вторая линия защиты — ограничения в БД: уникальный индекс активной брони на (event_id, user_id)
 *       и {@code CHECK (booked_count <= capacity)}.</li>
 * </ul>
 * Компромисс: при очень высокой конкуренции за одно событие (распродажа) повторов становится много, и
 * там лучше подошёл бы атомарный {@code UPDATE ... WHERE booked_count < capacity} или пессимистическая
 * блокировка. Подробности — в docs/decisions.md.
 *
 * <h2>Время</h2>
 * Текущее время берётся только из {@link Clock}, поэтому правило «отмена не позже N часов до начала»
 * проверяется в тестах детерминированно, без {@code Thread.sleep} и реального {@code now()}.
 */
@Service
public class BookingService {

    static final int MAX_ATTEMPTS = 5;

    private final EventRepository events;
    private final BookingRepository bookings;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final BookingProperties properties;
    private final BookingEventPublisher publisher;

    public BookingService(EventRepository events, BookingRepository bookings,
            PlatformTransactionManager transactionManager, Clock clock, BookingProperties properties, BookingEventPublisher publisher) {
        this.events = events;
        this.bookings = bookings;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.properties = properties;
        this.publisher = publisher;
    }

    public Booking book(long eventId, long userId) {
        try {
            Booking booking = inTransactionWithRetry(eventId, () -> bookOnce(eventId, userId));
            // the transaction has committed here: only now may the world hear about the booking
            publisher.publish(booking);
            return booking;
        } catch (DataIntegrityViolationException e) {
            if (bookings.existsByEventIdAndUserIdAndStatus(eventId, userId, BookingStatus.ACTIVE)) {
                throw new AlreadyBookedException(eventId, userId);
            }
            throw e;
        }
    }

    /**
     * Отменяет бронь и возвращает место. Отмена запрещена, если до начала события осталось меньше
     * {@code app.booking.cancellation-notice} (ровно столько — ещё можно).
     */
    public Booking cancel(long eventId, long bookingId) {
        return inTransactionWithRetry(eventId, () -> cancelOnce(eventId, bookingId));
    }

    /**
     * Не помечен @Transactional намеренно: каждая попытка выполняется в своей новой транзакции, иначе
     * повтор после конфликта версий шёл бы в уже откаченной транзакции и снова читал устаревшие данные.
     */
    private <T> T inTransactionWithRetry(long eventId, Supplier<T> action) {
        for (int attempt = 1; ; attempt++) {
            try {
                return transaction.execute(status -> action.get());
            } catch (OptimisticLockingFailureException e) {
                if (attempt == MAX_ATTEMPTS) {
                    throw new ConcurrentBookingException(eventId);
                }
            }
        }
    }

    private Booking bookOnce(long eventId, long userId) {
        Event event = events.findById(eventId).orElseThrow(() -> new EventNotFoundException(eventId));
        if (bookings.existsByEventIdAndUserIdAndStatus(eventId, userId, BookingStatus.ACTIVE)) {
            throw new AlreadyBookedException(eventId, userId);
        }
        event.book();
        // saveAndFlush: конфликт версии и нарушение уникальности проявляются здесь, внутри попытки
        return bookings.saveAndFlush(new Booking(eventId, userId));
    }

    private Booking cancelOnce(long eventId, long bookingId) {
        Event event = events.findById(eventId).orElseThrow(() -> new EventNotFoundException(eventId));
        Booking booking = bookings.findById(bookingId)
                .filter(found -> found.getEventId() == eventId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BookingAlreadyCancelledException(bookingId);
        }

        Instant now = clock.instant();
        Duration notice = properties.cancellationNotice();
        if (Duration.between(now, event.getStartsAt()).compareTo(notice) < 0) {
            throw new CancellationTooLateException(notice);
        }

        booking.cancel(now);
        event.release();
        return bookings.saveAndFlush(booking);
    }
}
