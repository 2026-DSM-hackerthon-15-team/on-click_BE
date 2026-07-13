package com.onclick.domain.store.dto;

import java.time.LocalTime;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.onclick.global.config.StrictLocalTimeDeserializer;

import jakarta.validation.constraints.Size;

public record UpdateStoreRequest(
        @Size(max = 100) String name,
        @Size(max = 50) String timeZone,
        @JsonDeserialize(using = StrictLocalTimeDeserializer.class) LocalTime closingTime
) {
}
