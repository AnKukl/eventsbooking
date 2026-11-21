package com.example.eventsbooking.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StorageProperties.class)
class StorageConfig {

    @Bean
    FileStorage fileStorage(StorageProperties properties) {
        return new LocalFileStorage(properties.localDir());
    }
}
