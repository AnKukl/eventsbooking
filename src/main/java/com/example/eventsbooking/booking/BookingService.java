package com.example.eventsbooking.booking;

import com.example.eventsbooking.event.Event;
import com.example.eventsbooking.event.EventNotFoundException;
import com.example.eventsbooking.event.EventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Бронирование места на мероприятие.
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
 *   <li>Версия защищает именно инвариант «занято &lt;= capacity»: каждая успешная бронь меняет
 *       {@code Event.bookedCount}, а значит и версию, поэтому две транзакции, прочитавшие одно и то же
 *       «осталось одно место», не смогут обе зафиксироваться.</li>
 *   <li>Вторая линия защиты — ограничения в БД: {@code UNIQUE (event_id, user_id)} (один пользователь —
 *       одна бронь) и {@code CHECK (booked_count <= capacity)}.</li>
 * </ul>
 * Компромисс: при очень высокой конкуренции за одно событие (распродажа) повторов становится много, и
 * там лучше подошёл бы атомарный {@code UPDATE ... WHERE booked_count < capacity} или пессимистическая
 * блокировка. Подробности — в docs/decisions.md.
 */
@Service
public class BookingService {

    static final int MAX_ATTEMPTS = 5;

    private final EventRepository events;
    private final BookingRepository bookings;
    private final TransactionTemplate transaction;

    public BookingService(EventRepository events, BookingRepository bookings,
            PlatformTransactionManager transactionManager) {
        this.events = events;
        this.bookings = bookings;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * Не помечен @Transactional намеренно: каждая попытка выполняется в своей новой транзакции, иначе
     * повтор после конфликта версий шёл бы в уже откаченной транзакции и снова читал устаревшие данные.
     */
    public Booking book(long eventId, long userId) {
        for (int attempt = 1; ; attempt++) {
            try {
                return transaction.execute(status -> bookOnce(eventId, userId));
            } catch (OptimisticLockingFailureException e) {
                if (attempt == MAX_ATTEMPTS) {
                    throw new ConcurrentBookingException(eventId);
                }
            } catch (DataIntegrityViolationException e) {
                if (bookings.existsByEventIdAndUserId(eventId, userId)) {
                    throw new AlreadyBookedException(eventId, userId);
                }
                throw e;
            }
        }
    }

    private Booking bookOnce(long eventId, long userId) {
        Event event = events.findById(eventId).orElseThrow(() -> new EventNotFoundException(eventId));
        if (bookings.existsByEventIdAndUserId(eventId, userId)) {
            throw new AlreadyBookedException(eventId, userId);
        }
        event.book();
        // saveAndFlush: конфликт версии и нарушение уникальности проявляются здесь, внутри попытки
        return bookings.saveAndFlush(new Booking(eventId, userId));
    }
}
