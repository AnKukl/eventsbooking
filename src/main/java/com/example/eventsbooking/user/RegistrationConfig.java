package com.example.eventsbooking.user;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({RegistrationProperties.class, RefreshProperties.class})
class RegistrationConfig {
}
