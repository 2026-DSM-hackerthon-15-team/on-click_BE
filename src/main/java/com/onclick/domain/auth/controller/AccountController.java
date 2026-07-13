package com.onclick.domain.auth.controller;

import com.onclick.domain.auth.dto.AccountProfileResponse;
import com.onclick.domain.auth.dto.ChangePasswordRequest;
import com.onclick.domain.auth.dto.UpdateAccountRequest;
import com.onclick.domain.auth.service.AccountService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/me")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public AccountProfileResponse getProfile(@AuthenticationPrincipal Jwt jwt) {
        return accountService.getProfile(jwt);
    }

    @PatchMapping
    public AccountProfileResponse updateProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateAccountRequest request
    ) {
        return accountService.updateProfile(jwt, request);
    }

    @PatchMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        accountService.changePassword(jwt, request);
    }
}
