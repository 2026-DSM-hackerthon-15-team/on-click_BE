package com.onclick.domain.chat.controller;

import java.util.List;

import com.onclick.domain.chat.dto.ChatMessageCreateRequest;
import com.onclick.domain.chat.dto.ChatMessageExchangeResponse;
import com.onclick.domain.chat.dto.ChatMessageResponse;
import com.onclick.domain.chat.dto.ChatRoomCreateRequest;
import com.onclick.domain.chat.dto.ChatRoomDetailResponse;
import com.onclick.domain.chat.dto.ChatRoomResponse;
import com.onclick.domain.chat.service.ChatService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stores/{storeId}/chat-rooms")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChatRoomResponse createRoom(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long storeId,
            @Valid @RequestBody ChatRoomCreateRequest request
    ) {
        return chatService.createRoom(jwt, storeId, request);
    }

    @GetMapping
    public List<ChatRoomResponse> findRooms(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long storeId
    ) {
        return chatService.findRooms(jwt, storeId);
    }

    @GetMapping("/{chatRoomId}")
    public ChatRoomDetailResponse findRoom(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long storeId,
            @PathVariable Long chatRoomId
    ) {
        return chatService.findRoom(jwt, storeId, chatRoomId);
    }

    @DeleteMapping("/{chatRoomId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRoom(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long storeId,
            @PathVariable Long chatRoomId
    ) {
        chatService.deleteRoom(jwt, storeId, chatRoomId);
    }

    @PostMapping("/{chatRoomId}/messages")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ChatMessageExchangeResponse sendMessage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long storeId,
            @PathVariable Long chatRoomId,
            @Valid @RequestBody ChatMessageCreateRequest request
    ) {
        return chatService.sendMessage(jwt, storeId, chatRoomId, request);
    }

    @GetMapping("/{chatRoomId}/messages")
    public List<ChatMessageResponse> findMessages(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long storeId,
            @PathVariable Long chatRoomId,
            @RequestParam(required = false) Long afterId
    ) {
        return chatService.findMessages(jwt, storeId, chatRoomId, afterId);
    }
}
