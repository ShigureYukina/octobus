package io.github.shigureyukina.octobus.relay.spi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.shigureyukina.octobus.common.ApiException;
import io.github.shigureyukina.octobus.relay.RelayInboundFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Parses an OpenAI Responses API request into the unified relay model.
 *
 * <p>MVP scope: extracts model, input (text), instructions, and options.
 * Multi-turn conversation "previous_response_id" is not yet mapped.
 */
public final class ResponsesInboundTransformer implements InboundTransformer {

    private final ObjectMapper objectMapper;

    public ResponsesInboundTransformer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public RelayInboundFormat inboundFormat() {
        return RelayInboundFormat.OPENAI_RESPONSES;
    }

    @Override
    public UnifiedRequest transform(String rawBody) {
        JsonNode root = readObject(rawBody);
        String model = textValue(root.get("model"));
        boolean stream = root.path("stream").asBoolean(false);

        Map<String, JsonNode> options = new LinkedHashMap<>();
        copyOption(root, options, "instructions");
        copyOption(root, options, "max_output_tokens");
        copyOption(root, options, "temperature");
        copyOption(root, options, "top_p");
        copyOption(root, options, "metadata");

        return new UnifiedRequest(
            UnifiedRequestType.CHAT,
            model,
            stream,
            List.of(),
            List.of(),
            Map.copyOf(options),
            root
        );
    }

    private JsonNode readObject(String rawBody) {
        try {
            JsonNode root = objectMapper.readTree(rawBody == null ? "{}" : rawBody);
            if (!root.isObject()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Responses request body must be a JSON object");
            }
            return root;
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid JSON request body", exception);
        }
    }

    private void copyOption(JsonNode root, Map<String, JsonNode> options, String field) {
        JsonNode value = root.get(field);
        if (value != null && !value.isNull()) {
            options.put(field, value);
        }
    }

    private String textValue(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }
}
