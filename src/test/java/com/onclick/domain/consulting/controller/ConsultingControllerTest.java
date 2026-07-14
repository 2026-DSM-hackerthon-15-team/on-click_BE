package com.onclick.domain.consulting.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.onclick.domain.consulting.dto.ConsultingCreateRequest;
import com.onclick.domain.consulting.dto.ConsultingDetailResponse;
import com.onclick.domain.consulting.entity.ConsultingStatus;
import com.onclick.domain.consulting.service.ConsultingCreationResult;
import com.onclick.domain.consulting.service.ConsultingService;
import com.onclick.global.error.GlobalExceptionHandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ConsultingControllerTest {

    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 7, 13);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 13, 22, 0);

    @Mock
    private ConsultingService consultingService;

    @Mock
    private Jwt jwt;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ConsultingController(consultingService))
                .setCustomArgumentResolvers(jwtArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void acceptsNewGenerationAndReturnsPendingResourceLocation() throws Exception {
        given(consultingService.generate(
                any(Jwt.class),
                org.mockito.ArgumentMatchers.eq(3L),
                org.mockito.ArgumentMatchers.eq(new ConsultingCreateRequest(TARGET_DATE))
        )).willReturn(new ConsultingCreationResult(response(), true));

        mockMvc.perform(post("/stores/3/consultings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetDate":"2026-07-13"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/stores/3/consultings/10"))
                .andExpect(jsonPath("$.consultingId").value(10L))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void returnsExistingResourceForRepeatedGenerationRequest() throws Exception {
        given(consultingService.generate(any(Jwt.class), any(), any()))
                .willReturn(new ConsultingCreationResult(response(), false));

        mockMvc.perform(post("/stores/3/consultings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetDate":"2026-07-13"}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("Location", "/stores/3/consultings/10"));
    }

    @Test
    void rejectsRequestWithoutTargetDate() throws Exception {
        mockMvc.perform(post("/stores/3/consultings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));

        verify(consultingService, never()).generate(any(), any(), any());
    }

    private ConsultingDetailResponse response() {
        return new ConsultingDetailResponse(
                10L,
                3L,
                TARGET_DATE,
                null,
                null,
                ConsultingStatus.PENDING,
                0,
                null,
                NOW,
                NOW
        );
    }

    private HandlerMethodArgumentResolver jwtArgumentResolver() {
        return new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType() == Jwt.class;
            }

            @Override
            public Object resolveArgument(
                    MethodParameter parameter,
                    ModelAndViewContainer mavContainer,
                    NativeWebRequest webRequest,
                    WebDataBinderFactory binderFactory
            ) {
                return jwt;
            }
        };
    }
}
