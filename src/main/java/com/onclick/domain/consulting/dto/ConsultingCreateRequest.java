package com.onclick.domain.consulting.dto;

import java.time.LocalDate;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ConsultingCreateRequest {

    private LocalDate targetDate;

    public ConsultingCreateRequest() {
    }

    public ConsultingCreateRequest(LocalDate targetDate) {
        this.targetDate = targetDate;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public void setTargetDate(LocalDate targetDate) {
        this.targetDate = targetDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConsultingCreateRequest that = (ConsultingCreateRequest) o;
        return Objects.equals(targetDate, that.targetDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(targetDate);
    }
}
