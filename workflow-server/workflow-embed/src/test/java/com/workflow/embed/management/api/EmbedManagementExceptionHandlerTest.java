package com.workflow.embed.management.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.workflow.core.result.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class EmbedManagementExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new EmbedManagementExceptionHandler())
                .build();
    }

    @Test
    void beanValidationFailureUsesStableHttp400Envelope() throws Exception {
        mockMvc.perform(post("/api/embed-management/v1/probe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.errorCode")
                        .value("EMBED_MANAGEMENT_REQUEST_INVALID"))
                .andExpect(jsonPath("$.data.violations[0].path").value("value"));
    }

    @Test
    void malformedJsonUsesStableHttp400Envelope() throws Exception {
        mockMvc.perform(post("/api/embed-management/v1/probe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.errorCode")
                        .value("EMBED_MANAGEMENT_REQUEST_INVALID"));
    }

    record ProbeRequest(@NotBlank String value) {
    }

    @RestController
    static class ProbeController {

        @PostMapping("/api/embed-management/v1/probe")
        ApiResponse<Void> validate(@Valid @RequestBody ProbeRequest request) {
            return ApiResponse.success();
        }
    }
}
