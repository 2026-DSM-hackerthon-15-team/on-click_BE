package com.onclick.domain.auth.controller;

import java.time.Instant;

import com.onclick.domain.auth.dto.AccountProfileResponse;
import com.onclick.domain.auth.dto.ChangePasswordRequest;
import com.onclick.domain.auth.service.AccountService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

    @Mock
    private AccountService accountService;
    @Mock
    private Jwt jwt;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AccountController(accountService))
                .setCustomArgumentResolvers(jwtArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsAuthenticatedAccountProfile() throws Exception {
        when(accountService.getProfile(any(Jwt.class))).thenReturn(new AccountProfileResponse(
                11L,
                "owner01",
                "홍길동",
                "owner@example.com",
                Instant.parse("2026-07-14T01:00:00Z"),
                Instant.parse("2026-07-14T02:00:00Z")
        ));

        mockMvc.perform(get("/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(11L))
                .andExpect(jsonPath("$.accountId").value("owner01"))
                .andExpect(jsonPath("$.name").value("홍길동"))
                .andExpect(jsonPath("$.email").value("owner@example.com"));
    }

    @Test
    void rejectsProfilePatchWithoutCurrentPassword() throws Exception {
        mockMvc.perform(patch("/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "새 이름"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @Test
    void changesPasswordWithNoContentResponse() throws Exception {
        mockMvc.perform(patch("/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "password123",
                                  "newPassword": "new-password123"
                                }
                                """))
                .andExpect(status().isNoContent());

        verify(accountService).changePassword(
                any(Jwt.class),
                org.mockito.ArgumentMatchers.eq(new ChangePasswordRequest(
                        "password123",
                        "new-password123"
                ))
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
