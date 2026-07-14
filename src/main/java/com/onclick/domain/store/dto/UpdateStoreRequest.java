package com.onclick.domain.store.dto;

import java.time.LocalTime;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.onclick.domain.store.entity.Industry;
import com.onclick.global.config.StrictLocalTimeDeserializer;

import jakarta.validation.constraints.Size;

public record UpdateStoreRequest(
        @Size(max = 100) String name,
        Industry industry,
        @Size(max = 255) String roadAddress,
        @JsonDeserialize(using = StrictLocalTimeDeserializer.class) LocalTime closingTime
) {

    public UpdateStoreRequest(String name, LocalTime closingTime) {
        this(name, null, null, closingTime);
    }
}
