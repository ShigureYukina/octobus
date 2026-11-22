package io.github.shigureyukina.octobus.relay.spi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.github.shigureyukina.octobus.common.ApiException;
import io.github.shigureyukina.octobus.relay.RelayInboundFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

public final class AnthropicMessagesInboundTransformer implements InboundTransformer {

    private final ObjectMapper objectMapper;

    public AnthropicMessagesInboundTransformer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public RelayInboundFormat inboundFormat() {
        return RelayInboundFormat.ANTHROPIC_MESSAGES;
    }

    @Override
    public UnifiedRequest transform(String rawBody) {
        JsonNode root = readObject(rawBody);
        List<UnifiedMessage> messages = new ArrayList<>();

        JsonNode systemNode = root.get("system");
        if (systemNode != null && !systemNode.isNull()) {
            messages.add(new UnifiedMessage("system", TextNode.valueOf(readTextOnlyContent(systemNode, "system"))));
        }

        messages.addAll(readMessages(root.path("messages")));

        return new UnifiedRequest(
            UnifiedRequestType.CHAT,
            textValue(root.get("model")),
            root.path("stream").asBoolean(false),
            List.copyOf(messages),
            List.of(),
            readOptions(root),
            root
        );
    }

    private JsonNode readObject(String rawBody) {
        try {
            JsonNode root = objectMapper.readTree(rawBody == null ? "{}" : rawBody);
            if (!root.isObject()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Anthropic Messages request body must be a JSON object");
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
            messages.add(new UnifiedMessage(
                textValue(item.get("role")),
                TextNode.valueOf(readTextOnlyContent(item.get("content"), "messages.content"))
            ));
        }
        return List.copyOf(messages);
    }

    private Map<String, JsonNode> readOptions(JsonNode root) {
        Map<String, JsonNode> options = new LinkedHashMap<>();
        copyOption(root, options, "max_tokens");
        copyOption(root, options, "temperature");
        copyOption(root, options, "top_p");
        return Map.copyOf(options);
    }

    private void copyOption(JsonNode root, Map<String, JsonNode> options, String field) {
        JsonNode value = root.get(field);
        if (value != null && !value.isNull()) {
            options.put(field, value);
        }
    }

    private String readTextOnlyContent(JsonNode content, String fieldName) {
        if (content == null || content.isNull()) {
            return "";
        }
        if (content.isTextual()) {
            return content.asText();
        }
        if (content instanceof ArrayNode arrayNode) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode block : arrayNode) {
                if (!"text".equals(textValue(block.get("type")))) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, fieldName + " only supports text content blocks");
                }
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                String text = textValue(block.get("text"));
                builder.append(text == null ? "" : text);
            }
            return builder.toString();
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, fieldName + " must be text-only");
    }

    private String textValue(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }
}
