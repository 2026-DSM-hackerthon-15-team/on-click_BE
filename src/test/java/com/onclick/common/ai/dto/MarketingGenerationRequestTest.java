package com.onclick.common.ai.dto;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketingGenerationRequestTest {

    @Test
    void normalizesDocumentedMarketingCopyRequestWithoutRequiringHttps() {
        MarketingGenerationRequest request = new MarketingGenerationRequest(
                7L,
                List.of(" http://localhost:8080/public/media/image "),
                "  신메뉴를 소개합니다.  ",
                List.of("  #신메뉴  "),
                "  친근하게  ",
                "  가격을 강조해 주세요.  "
        );

        assertThat(request.userId()).isEqualTo(7L);
        assertThat(request.imageUrls()).containsExactly("http://localhost:8080/public/media/image");
        assertThat(request.draftText()).isEqualTo("신메뉴를 소개합니다.");
        assertThat(request.tags()).containsExactly("#신메뉴");
        assertThat(request.tone()).isEqualTo("친근하게");
        assertThat(request.additionalRequest()).isEqualTo("가격을 강조해 주세요.");
    }

    @Test
    void rejectsValuesOutsideMarketingCopyContract() {
        assertThatThrownBy(() -> request(List.of(), "초안", List.of(), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("imageUrls");
        assertThatThrownBy(() -> request(
                List.of("https://cdn.example.com/image.jpg"),
                "가".repeat(2001),
                List.of(),
                null,
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("draftText");
        assertThatThrownBy(() -> request(
                List.of("https://cdn.example.com/image.jpg"),
                "초안",
                java.util.stream.IntStream.range(0, 31).mapToObj(index -> "tag-" + index).toList(),
                null,
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tags");
        assertThatThrownBy(() -> request(
                List.of("https://cdn.example.com/image.jpg"),
                "초안",
                List.of(),
                "가".repeat(101),
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tone");
        assertThatThrownBy(() -> request(
                List.of("https://cdn.example.com/image.jpg"),
                "초안",
                List.of(),
                null,
                "가".repeat(501)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("additionalRequest");
    }

    private MarketingGenerationRequest request(
            List<String> imageUrls,
            String draftText,
            List<String> tags,
            String tone,
            String additionalRequest
    ) {
        return new MarketingGenerationRequest(
                7L,
                imageUrls,
                draftText,
                tags,
                tone,
                additionalRequest
        );
    }
}
