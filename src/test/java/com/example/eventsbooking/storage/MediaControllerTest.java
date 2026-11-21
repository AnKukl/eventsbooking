package com.example.eventsbooking.storage;

import com.example.eventsbooking.security.SecurityConfig;
import com.example.eventsbooking.support.TestImages;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Uploaded pictures are public (event pages show them to guests) and served with the type chosen at upload time. */
@WebMvcTest(MediaController.class)
@Import(SecurityConfig.class)
@WithAnonymousUser
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class MediaControllerTest {

    private static final String NAME = "0d9f2f5e-7c63-4c1f-9a60-0a3f7d1c7c11.png";

    private final MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private FileStorage storage;

    MediaControllerTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void aStoredPictureIsServedToAnyoneWithItsTypeAndLongCaching() throws Exception {
        when(storage.read(NAME)).thenReturn(Optional.of(TestImages.png()));

        mockMvc.perform(get("/api/media/" + NAME))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("max-age=31536000")))
                .andExpect(content().bytes(TestImages.png()));
    }

    @Test
    void theTypeFollowsTheStoredExtension() throws Exception {
        when(storage.read("0d9f2f5e-7c63-4c1f-9a60-0a3f7d1c7c11.jpg")).thenReturn(Optional.of(TestImages.jpeg()));
        when(storage.read("0d9f2f5e-7c63-4c1f-9a60-0a3f7d1c7c11.webp")).thenReturn(Optional.of(TestImages.webp()));

        mockMvc.perform(get("/api/media/0d9f2f5e-7c63-4c1f-9a60-0a3f7d1c7c11.jpg"))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"));
        mockMvc.perform(get("/api/media/0d9f2f5e-7c63-4c1f-9a60-0a3f7d1c7c11.webp"))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/webp"));
    }

    @Test
    void aMissingFileIs404() throws Exception {
        when(storage.read(NAME)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/media/" + NAME)).andExpect(status().isNotFound());
    }

    @Test
    void namesThatAreNotOurGeneratedOnesNeverReachTheStorage() throws Exception {
        for (String name : new String[] {"..%2F..%2Fetc%2Fpasswd", "secret.txt", "x.exe", "0d9f2f5e.png",
                "0d9f2f5e-7c63-4c1f-9a60-0a3f7d1c7c11.svg", "%2e%2e%2fapplication.properties"}) {
            mockMvc.perform(get("/api/media/" + name)).andExpect(status().is4xxClientError());
        }
        verifyNoInteractions(storage);
    }
}
