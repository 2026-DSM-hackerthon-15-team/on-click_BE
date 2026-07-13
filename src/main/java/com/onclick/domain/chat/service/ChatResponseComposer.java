package com.onclick.domain.chat.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Component;

@Component
class ChatResponseComposer {

    Optional<String> actionResponse(Long storeId, String userMessage) {
        String normalized = userMessage.toLowerCase(Locale.ROOT);
        List<String> links = new ArrayList<>();

        if (containsAny(normalized, "컨설팅", "consulting", "consult")) {
            links.add("[컨설팅 페이지](/stores/" + storeId + "/consultings)");
        }
        if (containsAny(normalized, "마케팅", "홍보", "인스타", "instagram", "marketing")) {
            links.add("[마케팅 페이지](/stores/" + storeId + "/marketings)");
        }
        if (links.isEmpty()) {
            return Optional.empty();
        }

        String body = links.stream()
                .map(link -> "- " + link)
                .reduce((left, right) -> left + "\n" + right)
                .orElseThrow();
        return Optional.of("요청하신 기능은 아래 페이지에서 진행해 주세요.\n\n" + body);
    }

    String normalizeGeneratedResponse(String generatedContent) {
        String normalized = generatedContent == null ? "" : generatedContent.trim();
        if (normalized.isEmpty()) {
            throw new IllegalStateException("Chat generator returned an empty response");
        }
        return normalized;
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
