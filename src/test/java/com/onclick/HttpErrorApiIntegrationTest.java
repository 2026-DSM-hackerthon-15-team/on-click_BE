package com.onclick;

import com.onclick.global.error.ErrorCode;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class HttpErrorApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unknownPublicPathReturnsNotFound() throws Exception {
        assertErrorResponse(
                mockMvc.perform(get("/public/media/missing/route")),
                ErrorCode.NOT_FOUND
        );
    }

    @Test
    void unsupportedMethodReturnsMethodNotAllowedWithAllowHeader() throws Exception {
        mockMvc.perform(get("/auth/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, "POST"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.METHOD_NOT_ALLOWED.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.METHOD_NOT_ALLOWED.defaultMessage()));
    }

    private void assertErrorResponse(
            ResultActions result,
            ErrorCode errorCode
    ) throws Exception {
        result.andExpect(status().is(errorCode.status().value()))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.errorCode").value(errorCode.name()))
                .andExpect(jsonPath("$.message").value(errorCode.defaultMessage()));
    }
}
