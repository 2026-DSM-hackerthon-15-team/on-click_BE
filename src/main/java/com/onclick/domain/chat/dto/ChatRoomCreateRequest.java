package com.onclick.domain.chat.dto;

import jakarta.validation.constraints.Size;

public record ChatRoomCreateRequest(
        @Size(max = 100) String title
) {
}
