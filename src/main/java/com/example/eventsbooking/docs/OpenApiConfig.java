package com.example.eventsbooking.docs;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    OpenAPI eventsBookingOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Events Booking API").version("v1")
                        .description("Events and bookings. Get a token from POST /api/auth/login and press "
                                + "Authorize; errors follow RFC 9457 (application/problem+json)."))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                // applies to all operations; /api/auth/* and health are public, the scheme is just ignored there
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
