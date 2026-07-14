package com.onclick.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChatResponseComposerTest {

    private final ChatResponseComposer composer = new ChatResponseComposer();

    @Test
    void trimsGeneratedResponse() {
        assertThat(composer.normalizeGeneratedResponse("  AI 답변  ")).isEqualTo("AI 답변");
    }

    @Test
    void rejectsBlankGeneratedResponse() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> composer.normalizeGeneratedResponse("  "))
                .isInstanceOf(IllegalStateException.class);
    }
}
