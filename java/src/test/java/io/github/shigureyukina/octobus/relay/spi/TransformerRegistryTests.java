package io.github.shigureyukina.octobus.relay.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.shigureyukina.octobus.common.ApiException;
import io.github.shigureyukina.octobus.relay.RelayInboundFormat;
import org.junit.jupiter.api.Test;

class TransformerRegistryTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void openAiChatInboundAndOutboundResolveThroughRegistry() {
        TransformerRegistry registry = TransformerRegistry.defaultRegistry(objectMapper);

        InboundTransformer inbound = registry.resolveInbound(RelayInboundFormat.OPENAI_CHAT_COMPLETION);
        OutboundTransformer outbound = registry.resolveOutbound(RelayProviderFormat.OPENAI_CHAT_COMPLETION);

        assertThat(inbound).isInstanceOf(OpenAIChatInboundTransformer.class);
        assertThat(outbound).isInstanceOf(OpenAIChatOutboundTransformer.class);
        assertThat(inbound.isGuarded()).isFalse();
        assertThat(outbound.isGuarded()).isFalse();
    }

    @Test
    void openAiResponsesInboundNowHasRealTransformer() {
        TransformerRegistry registry = TransformerRegistry.defaultRegistry(objectMapper);

        InboundTransformer inbound = registry.resolveInbound(RelayInboundFormat.OPENAI_RESPONSES);

        assertThat(inbound).isNotNull();
        assertThat(inbound.isGuarded()).isFalse();
        assertThat(inbound).isInstanceOf(ResponsesInboundTransformer.class);
    }

    @Test
    void registryResolvesGeminiOutboundTransformer() {
        TransformerRegistry registry = TransformerRegistry.defaultRegistry(objectMapper);

        OutboundTransformer outbound = registry.resolveOutbound(RelayProviderFormat.GEMINI_CONTENTS);

        assertThat(outbound).isNotNull();
        assertThat(outbound.isGuarded()).isFalse();
        assertThat(outbound.supports(UnifiedRequestType.CHAT)).isTrue();
        assertThat(outbound).isInstanceOf(GeminiContentsOutboundTransformer.class);
    }

    @Test
    void openAiChatInboundNormalizesMessagesToolsAndOptions() {
        OpenAIChatInboundTransformer transformer = new OpenAIChatInboundTransformer(objectMapper);
        UnifiedRequest request = transformer.transform("""
            {
              "model": "gpt-4o",
              "stream": false,
              "messages": [
                {"role": "system", "content": "You are helpful"},
                {"role": "user", "content": "Hi"}
              ],
              "tools": [
                {
                  "type": "function",
                  "function": {
                    "name": "lookup_weather",
                    "description": "Get weather",
                    "parameters": {"type": "object"}
                  }
                }
              ],
              "temperature": 0.2,
              "top_p": 0.9
            }
            """);

        assertThat(request.requestType()).isEqualTo(UnifiedRequestType.CHAT);
        assertThat(request.model()).isEqualTo("gpt-4o");
        assertThat(request.stream()).isFalse();
        assertThat(request.messages()).hasSize(2);
        assertThat(request.messages().get(0).role()).isEqualTo("system");
        assertThat(request.tools()).hasSize(1);
        assertThat(request.tools().get(0).name()).isEqualTo("lookup_weather");
        assertThat(request.options()).containsKeys("temperature", "top_p");
        assertThat(request.rawPayload().get("model").asText()).isEqualTo("gpt-4o");
    }

    @Test
    void openAiChatOutboundSerializesUnifiedRequest() {
        OpenAIChatOutboundTransformer transformer = new OpenAIChatOutboundTransformer(objectMapper);
        UnifiedRequest request = new UnifiedRequest(
            UnifiedRequestType.CHAT,
            "gpt-4o",
            false,
            java.util.List.of(new UnifiedMessage("user", objectMapper.getNodeFactory().textNode("hello"))),
            java.util.List.of(),
            java.util.Map.of("temperature", objectMapper.getNodeFactory().numberNode(0.1)),
            objectMapper.createObjectNode()
        );

        JsonNode payload = transformer.serializeRequest(request);

        assertThat(payload.get("model").asText()).isEqualTo("gpt-4o");
        assertThat(payload.get("stream").asBoolean()).isFalse();
        assertThat(payload.get("messages")).hasSize(1);
        assertThat(payload.get("temperature").decimalValue()).isEqualTo(new java.math.BigDecimal("0.1"));
    }

    @Test
    void openAiChatOutboundParsesUsageFromResponse() {
        OpenAIChatOutboundTransformer transformer = new OpenAIChatOutboundTransformer(objectMapper);

        UnifiedResponse response = transformer.parseResponse("""
            {
              "model": "gpt-4o-2024-08-06",
              "choices": [{"message": {"role": "assistant", "content": "Hello"}}],
              "usage": {
                "prompt_tokens": 12,
                "completion_tokens": 5,
                "total_tokens": 17
              }
            }
            """);

        assertThat(response.model()).isEqualTo("gpt-4o-2024-08-06");
        assertThat(response.usage().inputTokens()).isEqualTo(12);
        assertThat(response.usage().outputTokens()).isEqualTo(5);
        assertThat(response.usage().totalTokens()).isEqualTo(17);
        assertThat(response.primaryContent()).isNotNull();
    }
}
