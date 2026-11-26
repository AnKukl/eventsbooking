package com.example.eventsbooking.booking;

import com.example.eventsbooking.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The booking form reads its limits from the server instead of hard-coding them. */
@WebMvcTest(controllers = BookingSettingsController.class, properties = {
        "app.booking.max-seats-per-booking=6", "app.booking.hold-duration=15m", "app.booking.cancellation-notice=48h"})
@Import(SecurityConfig.class)
@EnableConfigurationProperties(BookingProperties.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class BookingSettingsControllerTest {

    private final MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    BookingSettingsControllerTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void theLimitsAreOpenToGuestsAndComeFromTheConfiguration() throws Exception {
        mockMvc.perform(get("/api/settings/booking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxSeatsPerBooking").value(6))
                .andExpect(jsonPath("$.holdDurationSeconds").value(900))
                .andExpect(jsonPath("$.cancellationNoticeHours").value(48));
    }
}
