package com.example.eventsbooking.docs;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** In production the API description and Swagger UI are switched off. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class OpenApiProdProfileTest {

    private final MockMvc mockMvc;

    OpenApiProdProfileTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void theApiDescriptionIsNotServed() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
    }

    @Test
    void swaggerUiIsNotServed() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
    }
}
