package com.onclick.domain.chat.generation;

@FunctionalInterface
public interface ChatGenerationPort {

    String generate(ChatGenerationRequest request);
}
