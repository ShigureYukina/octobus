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
 * Parses an OpenAI Images API request (generation / edit / variation)
 * into the unified relay model.
 *
 * <p>MVP scope: extracts prompt, model, n, size, quality, style.
 * Image binary data for edit/variation is preserved in rawPayload
 * and passed through as-is.
 */
public final class OpenAIImageInboundTransformer implements InboundTransformer {

    private final ObjectMapper objectMapper;

    public OpenAIImageInboundTransformer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public RelayInboundFormat inboundFormat() {
        // This transformer is used for all three image routes.
        // The specific inbound format is set per-route at registration time.
        return RelayInboundFormat.OPENAI_IMAGE_GENERATION;
    }

    @Override
    public UnifiedRequest transform(String rawBody) {
        JsonNode root = readObject(rawBody);
        String model = textValue(root.get("model"));

        Map<String, JsonNode> options = new LinkedHashMap<>();
        copyOption(root, options, "prompt");
        copyOption(root, options, "n");
        copyOption(root, options, "size");
        copyOption(root, options, "quality");
        copyOption(root, options, "style");
        copyOption(root, options, "response_format");
        copyOption(root, options, "user");

        return new UnifiedRequest(
            UnifiedRequestType.IMAGE,
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
                throw new ApiException(HttpStatus.BAD_REQUEST, "Image request body must be a JSON object");
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
