package io.github.shigureyukina.octobus.relay.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.shigureyukina.octobus.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AnthropicMessagesInboundTransformerTests {

    private final AnthropicMessagesInboundTransformer transformer =
        new AnthropicMessagesInboundTransformer(new ObjectMapper());

    @Test
    void transformsTextOnlyAnthropicRequest() {
        UnifiedRequest request = transformer.transform("""
            {
              "model": "claude-3-5-sonnet",
              "system": "You are helpful",
              "messages": [
                {"role": "user", "content": "Hello"},
                {"role": "assistant", "content": [{"type": "text", "text": "Hi"}]}
              ],
              "max_tokens": 128,
              "temperature": 0.3,
              "top_p": 0.9,
              "stream": false
            }
            """);

        assertThat(request.requestType()).isEqualTo(UnifiedRequestType.CHAT);
        assertThat(request.model()).isEqualTo("claude-3-5-sonnet");
        assertThat(request.stream()).isFalse();
        assertThat(request.messages()).hasSize(3);
        assertThat(request.messages().get(0).role()).isEqualTo("system");
        assertThat(request.messages().get(0).content().asText()).isEqualTo("You are helpful");
        assertThat(request.messages().get(1).content().asText()).isEqualTo("Hello");
        assertThat(request.messages().get(2).content().asText()).isEqualTo("Hi");
        assertThat(request.options()).containsKeys("max_tokens", "temperature", "top_p");
    }

    @Test
    void transformsSystemArrayIntoSingleSystemMessage() {
        UnifiedRequest request = transformer.transform("""
            {
              "model": "claude-3-5-sonnet",
              "system": [
                {"type": "text", "text": "Line 1"},
                {"type": "text", "text": "Line 2"}
              ],
              "messages": []
            }
            """);

        assertThat(request.messages()).hasSize(1);
        assertThat(request.messages().get(0).content().asText()).isEqualTo("Line 1\nLine 2");
    }

    @Test
    void rejectsNonTextContentBlocks() {
        assertThatThrownBy(() -> transformer.transform("""
            {
              "model": "claude-3-5-sonnet",
              "messages": [
                {"role": "user", "content": [{"type": "image", "source": {}}]}
              ]
            }
            """))
            .isInstanceOf(ApiException.class)
            .satisfies(exception -> {
                ApiException apiException = (ApiException) exception;
                assertThat(apiException.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(apiException.getClientMessage()).contains("text content blocks");
            });
    }
}
