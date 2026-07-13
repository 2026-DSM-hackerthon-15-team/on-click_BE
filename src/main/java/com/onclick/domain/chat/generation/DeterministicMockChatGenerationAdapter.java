package com.onclick.domain.chat.generation;

public final class DeterministicMockChatGenerationAdapter implements ChatGenerationPort {

    @Override
    public String generate(ChatGenerationRequest request) {
        return "질문을 확인했습니다: " + request.message()
                + "\n\n현재 매장 데이터를 바탕으로 실행 가능한 다음 단계를 정리해 보세요.";
    }
}
