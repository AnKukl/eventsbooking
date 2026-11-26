package com.example.eventsbooking.docs;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    private static final String BEARER = "bearerAuth";
    private static final String COOKIE = "cookieAuth";

    @Bean
    OpenAPI eventsBookingOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Events Booking API").version("v1")
                        .description("Events and bookings. POST /api/auth/login sets the session cookies (the browser "
                                + "sends them by itself); scripts may send the token as a Bearer header instead. "
                                + "Errors follow RFC 9457 (application/problem+json, schema ProblemDetail)."))
                .components(new Components()
                        .addSecuritySchemes(BEARER, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"))
                        .addSecuritySchemes(COOKIE, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.COOKIE).name("access_token")))
                // either scheme; /api/auth/*, health and the public catalogue simply ignore them
                .addSecurityItem(new SecurityRequirement().addList(BEARER))
                .addSecurityItem(new SecurityRequirement().addList(COOKIE));
    }

    /** Added after generation: springdoc rebuilds the schema list and would drop a schema set on the bean above. */
    @Bean
    org.springdoc.core.customizers.OpenApiCustomizer problemDetailSchema() {
        return openApi -> openApi.getComponents().addSchemas("ProblemDetail", problemDetail());
    }

    /** RFC 9457 body of every error; {@code errors} lists the fields for 400 (validation) and 422 (business rules). */
    @SuppressWarnings("rawtypes")
    private static ObjectSchema problemDetail() {
        ObjectSchema violation = new ObjectSchema();
        violation.addProperty("field", new StringSchema());
        violation.addProperty("message", new StringSchema());
        ObjectSchema schema = new ObjectSchema();
        schema.description("Error body (RFC 9457, application/problem+json)");
        schema.addProperty("type", new StringSchema().format("uri"));
        schema.addProperty("title", new StringSchema());
        schema.addProperty("status", new IntegerSchema());
        schema.addProperty("detail", new StringSchema());
        schema.addProperty("instance", new StringSchema().format("uri"));
        schema.addProperty("errors", new ArraySchema().items(violation));
        schema.addProperty("code", new StringSchema().description("409 only: a stable code such as EVENT_IS_FULL"));
        return schema;
    }
}
