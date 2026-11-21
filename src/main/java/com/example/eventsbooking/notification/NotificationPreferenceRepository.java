package com.example.eventsbooking.notification;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class NotificationPreferenceRepository {

    private final JdbcClient jdbc;

    public NotificationPreferenceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<NotificationPreference> findByUserId(long userId) {
        return jdbc.sql("SELECT type, email, push FROM notification_preferences WHERE user_id = :userId")
                .param("userId", userId)
                .query((rs, row) -> new NotificationPreference(NotificationType.valueOf(rs.getString("type")),
                        rs.getBoolean("email"), rs.getBoolean("push")))
                .list();
    }

    /** One statement: concurrent saves of the same (user, type) cannot collide. */
    public void upsert(long userId, NotificationPreference preference) {
        jdbc.sql("""
                INSERT INTO notification_preferences (user_id, type, email, push)
                VALUES (:userId, :type, :email, :push)
                ON CONFLICT (user_id, type) DO UPDATE SET email = EXCLUDED.email, push = EXCLUDED.push
                """)
                .param("userId", userId)
                .param("type", preference.type().name())
                .param("email", preference.email())
                .param("push", preference.push())
                .update();
    }
}
