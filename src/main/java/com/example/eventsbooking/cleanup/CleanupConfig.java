package com.example.eventsbooking.cleanup;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CleanupProperties.class)
class CleanupConfig {

    /** The lock expiry is judged by the database clock, so instances with different system clocks agree. */
    @Bean
    LockProvider lockProvider(JdbcTemplate jdbc) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder().withJdbcTemplate(jdbc).usingDbTime().build());
    }

    @Bean
    ClusterLock clusterLock(LockProvider provider, Clock clock) {
        return new ClusterLock(provider, clock);
    }
}
