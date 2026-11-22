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
 * Parses an OpenAI Embeddings API request into the unified relay model.
 *
 * <p>MVP scope: extracts model and input (string or array). Other
 * parameters (encoding_format, dimensions, user) are preserved in rawPayload.
 */
public final class OpenAIEmbeddingsInboundTransformer implements InboundTransformer {

    private final ObjectMapper objectMapper;

    public OpenAIEmbeddingsInboundTransformer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public RelayInboundFormat inboundFormat() {
        return RelayInboundFormat.OPENAI_EMBEDDINGS;
    }

    @Override
    public UnifiedRequest transform(String rawBody) {
        JsonNode root = readObject(rawBody);
        String model = textValue(root.get("model"));

        Map<String, JsonNode> options = new LinkedHashMap<>();
        copyOption(root, options, "encoding_format");
        copyOption(root, options, "dimensions");
        copyOption(root, options, "user");

        return new UnifiedRequest(
            UnifiedRequestType.EMBEDDING,
            model,
            false,
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
                throw new ApiException(HttpStatus.BAD_REQUEST, "Embeddings request body must be a JSON object");
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
