package com.onclick.domain.chat.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import com.onclick.domain.chat.generation.ChatGenerationPort;
import com.onclick.domain.chat.generation.ChatGenerationRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatMessageProcessorTest {

    @Mock
    private ChatMessageWorkService workService;

    @Mock
    private ChatGenerationPort generationPort;

    private ChatMessageProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new ChatMessageProcessor(
                workService,
                generationPort,
                new ChatResponseComposer()
        );
    }

    @Test
    void completesClaimWithGeneratedResponse() {
        ChatGenerationWork work = work("오늘 매출을 알려줘");
        given(workService.claim(101L)).willReturn(Optional.of(work));
        given(generationPort.generate(work.request())).willReturn("  매출 답변  ");

        processor.process(101L);

        verify(workService).complete(work, "매출 답변");
        verify(workService, never()).fail(work);
    }

    @Test
    void usesServerGeneratedStoreScopedLinkForConsultingIntent() {
        ChatGenerationWork work = work("컨설팅 결과를 보고 싶어요");
        given(workService.claim(101L)).willReturn(Optional.of(work));

        processor.process(101L);

        verify(workService).complete(
                work,
                "요청하신 기능은 아래 페이지에서 진행해 주세요.\n\n"
                        + "- [컨설팅 페이지](/stores/3/consultings)"
        );
        verify(generationPort, never()).generate(work.request());
    }

    @Test
    void schedulesFailureWhenGeneratorFails() {
        ChatGenerationWork work = work("오늘 매출을 알려줘");
        given(workService.claim(101L)).willReturn(Optional.of(work));
        given(generationPort.generate(work.request())).willThrow(new RuntimeException("timeout"));

        processor.process(101L);

        verify(workService).fail(work);
        verify(workService, never()).complete(
                org.mockito.ArgumentMatchers.eq(work),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    private ChatGenerationWork work(String message) {
        return new ChatGenerationWork(
                101L,
                1,
                new ChatGenerationRequest(3L, 10L, 100L, message, List.of())
        );
    }
}
