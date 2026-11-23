package com.example.eventsbooking.docs;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The API contract the frontend is built against is kept in docs/openapi.json. Any change of the generated description
 * fails this test until the snapshot is updated on purpose:
 * {@code .\mvnw.cmd test -Dtest=OpenApiSnapshotTest -Dopenapi.snapshot.update=true}, then review the diff.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class OpenApiSnapshotTest {

    private static final Path SNAPSHOT = Path.of("docs", "openapi.json");
    private static final ObjectMapper JSON = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

    private final MockMvc mockMvc;

    OpenApiSnapshotTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void theGeneratedDescriptionMatchesTheSnapshot() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode current = JSON.readTree(body);

        if (Boolean.getBoolean("openapi.snapshot.update")) {
            Files.writeString(SNAPSHOT, JSON.writeValueAsString(current) + "\n", StandardCharsets.UTF_8);
            return;
        }
        assertThat(Files.exists(SNAPSHOT)).as("%s is missing; create it with -Dopenapi.snapshot.update=true", SNAPSHOT)
                .isTrue();
        JsonNode snapshot = JSON.readTree(Files.readString(SNAPSHOT, StandardCharsets.UTF_8));
        assertThat(current)
                .as("The API description changed. If that is intended, run "
                        + ".\\mvnw.cmd test -Dtest=OpenApiSnapshotTest -Dopenapi.snapshot.update=true and commit docs/openapi.json")
                .isEqualTo(snapshot);
    }
}
