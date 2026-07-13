package com.onclick.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChatResponseComposerTest {

    private final ChatResponseComposer composer = new ChatResponseComposer();

    @Test
    void returnsBothActionLinksWhenBothIntentsArePresent() {
        assertThat(composer.actionResponse(7L, "컨설팅과 인스타 홍보를 진행해 줘"))
                .hasValueSatisfying(response -> assertThat(response)
                        .contains("[컨설팅 페이지](/stores/7/consultings)")
                        .contains("[마케팅 페이지](/stores/7/marketings)"));
    }

    @Test
    void returnsNoActionForOrdinaryQuestion() {
        assertThat(composer.actionResponse(7L, "오늘 매출이 얼마야?")).isEmpty();
    }
}
