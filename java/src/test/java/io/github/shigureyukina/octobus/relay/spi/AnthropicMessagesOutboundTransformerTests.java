package io.github.shigureyukina.octobus.relay.spi;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnthropicMessagesOutboundTransformerTests {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AnthropicMessagesOutboundTransformer transformer =
        new AnthropicMessagesOutboundTransformer(objectMapper);

    @Test
    void serializesUnifiedRequestIntoAnthropicPayload() {
        UnifiedRequest request = new UnifiedRequest(
            UnifiedRequestType.CHAT,
            "claude-3-5-sonnet",
            false,
            List.of(
                new UnifiedMessage("system", TextNode.valueOf("You are helpful")),
                new UnifiedMessage("user", TextNode.valueOf("Hello"))
            ),
            List.of(),
            Map.of("max_tokens", objectMapper.valueToTree(256), "temperature", objectMapper.valueToTree(0.2)),
            null
        );

        JsonNode payload = transformer.serializeRequest(request);
        assertThat(payload.get("model").asText()).isEqualTo("claude-3-5-sonnet");
        assertThat(payload.get("system").asText()).isEqualTo("You are helpful");
        assertThat(payload.get("messages")).hasSize(1);
        assertThat(payload.get("messages").get(0).get("content").asText()).isEqualTo("Hello");
        assertThat(payload.get("max_tokens").asInt()).isEqualTo(256);
    }

    @Test
    void parsesAnthropicResponseIntoUnifiedResponse() {
        UnifiedResponse response = transformer.parseResponse("""
            {
              "id": "msg_123",
              "model": "claude-3-5-sonnet-20240620",
              "content": [
                {"type": "text", "text": "Hello from Claude"}
              ],
              "usage": {
                "input_tokens": 12,
                "output_tokens": 5
              },
              "stop_reason": "end_turn"
            }
            """);

        assertThat(response.model()).isEqualTo("claude-3-5-sonnet-20240620");
        assertThat(response.primaryContent().asText()).isEqualTo("Hello from Claude");
        assertThat(response.usage().inputTokens()).isEqualTo(12);
        assertThat(response.usage().outputTokens()).isEqualTo(5);
        assertThat(response.usage().totalTokens()).isEqualTo(17);
        assertThat(response.rawPayload().get("stop_reason").asText()).isEqualTo("end_turn");
    }
}
