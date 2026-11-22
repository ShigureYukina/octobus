package io.github.shigureyukina.octobus.relay.spi;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GeminiContentsOutboundTransformerTests {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GeminiContentsOutboundTransformer transformer =
        new GeminiContentsOutboundTransformer(objectMapper);

    @Test
    void serializesUnifiedRequestIntoGeminiPayload() {
        UnifiedRequest request = new UnifiedRequest(
            UnifiedRequestType.CHAT,
            "gemini-2.5-flash",
            false,
            List.of(
                new UnifiedMessage("system", TextNode.valueOf("You are helpful")),
                new UnifiedMessage("user", TextNode.valueOf("Hello")),
                new UnifiedMessage("assistant", TextNode.valueOf("Hi there"))
            ),
            List.of(),
            Map.of(
                "max_tokens", objectMapper.valueToTree(256),
                "temperature", objectMapper.valueToTree(0.2),
                "top_p", objectMapper.valueToTree(0.9)
            ),
            null
        );

        JsonNode payload = transformer.serializeRequest(request);
        assertThat(payload.get("model").asText()).isEqualTo("gemini-2.5-flash");
        assertThat(payload.get("systemInstruction").get("parts").get(0).get("text").asText()).isEqualTo("You are helpful");
        assertThat(payload.get("contents")).hasSize(2);
        assertThat(payload.get("contents").get(0).get("role").asText()).isEqualTo("user");
        assertThat(payload.get("contents").get(1).get("role").asText()).isEqualTo("model");
        assertThat(payload.get("generationConfig").get("maxOutputTokens").asInt()).isEqualTo(256);
    }

    @Test
    void parsesGeminiResponseIntoOpenAiCompatiblePayload() {
        UnifiedResponse response = transformer.parseResponse("""
            {
              "responseId": "gemini-123",
              "modelVersion": "gemini-2.5-flash",
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {"text": "Hello from Gemini"}
                    ]
                  },
                  "finishReason": "STOP"
                }
              ],
              "usageMetadata": {
                "promptTokenCount": 12,
                "candidatesTokenCount": 5,
                "totalTokenCount": 17
              }
            }
            """);

        assertThat(response.model()).isEqualTo("gemini-2.5-flash");
        assertThat(response.primaryContent().asText()).isEqualTo("Hello from Gemini");
        assertThat(response.usage().inputTokens()).isEqualTo(12);
        assertThat(response.usage().outputTokens()).isEqualTo(5);
        assertThat(response.usage().totalTokens()).isEqualTo(17);
        assertThat(response.rawPayload().get("choices").get(0).get("message").get("content").asText())
            .isEqualTo("Hello from Gemini");
    }
}
