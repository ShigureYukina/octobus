package io.github.shigureyukina.octobus.relay.spi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.github.shigureyukina.octobus.common.ApiException;
import io.github.shigureyukina.octobus.relay.RelayInboundFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Minimal OpenAI Chat inbound transformer used to establish the T2 SPI contract.
 */
public final class OpenAIChatInboundTransformer implements InboundTransformer {

    private final ObjectMapper objectMapper;

    public OpenAIChatInboundTransformer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public RelayInboundFormat inboundFormat() {
        return RelayInboundFormat.OPENAI_CHAT_COMPLETION;
    }

    @Override
    public UnifiedRequest transform(String rawBody) {
        JsonNode root = readObject(rawBody);
        List<UnifiedMessage> messages = readMessages(root.path("messages"));
        List<UnifiedTool> tools = readTools(root.path("tools"));
        Map<String, JsonNode> options = readOptions(root);

        return new UnifiedRequest(
            UnifiedRequestType.CHAT,
            textValue(root.get("model")),
            root.path("stream").asBoolean(false),
            messages,
            tools,
            options,
            root
        );
    }

    private JsonNode readObject(String rawBody) {
        try {
            JsonNode root = objectMapper.readTree(rawBody == null ? "{}" : rawBody);
            if (!root.isObject()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "OpenAI Chat request body must be a JSON object");
            }
            return root;
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid JSON request body", exception);
        }
    }

    private List<UnifiedMessage> readMessages(JsonNode node) {
        if (!(node instanceof ArrayNode arrayNode)) {
            return List.of();
        }

        List<UnifiedMessage> messages = new ArrayList<>(arrayNode.size());
        for (JsonNode item : arrayNode) {
            messages.add(new UnifiedMessage(textValue(item.get("role")), item.get("content")));
        }
        return List.copyOf(messages);
    }

    private List<UnifiedTool> readTools(JsonNode node) {
        if (!(node instanceof ArrayNode arrayNode)) {
            return List.of();
        }

        List<UnifiedTool> tools = new ArrayList<>(arrayNode.size());
        for (JsonNode item : arrayNode) {
            JsonNode function = item.path("function");
            tools.add(new UnifiedTool(
                textValue(function.get("name")),
                textValue(function.get("description")),
                function.get("parameters")
            ));
        }
        return List.copyOf(tools);
    }

    private Map<String, JsonNode> readOptions(JsonNode root) {
        Map<String, JsonNode> options = new LinkedHashMap<>();
        copyOption(root, options, "temperature");
        copyOption(root, options, "top_p");
        copyOption(root, options, "max_tokens");
        copyOption(root, options, "response_format");
        return Map.copyOf(options);
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
