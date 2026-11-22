package io.github.shigureyukina.octobus.relay.spi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.github.shigureyukina.octobus.common.ApiException;
import org.springframework.http.HttpStatus;

public final class AnthropicMessagesOutboundTransformer implements OutboundTransformer {

    private final ObjectMapper objectMapper;

    public AnthropicMessagesOutboundTransformer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public RelayProviderFormat providerFormat() {
        return RelayProviderFormat.ANTHROPIC_MESSAGE;
    }

    @Override
    public boolean supports(UnifiedRequestType requestType) {
        return requestType == UnifiedRequestType.CHAT;
    }

    @Override
    public JsonNode serializeRequest(UnifiedRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unified request is required");
        }
        if (!supports(request.requestType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Anthropic Messages outbound only supports CHAT requests");
        }

        ObjectNode root = objectMapper.createObjectNode();
        if (request.model() != null) {
            root.put("model", request.model());
        }
        root.put("stream", request.stream());

        ArrayNode messages = root.putArray("messages");
        String system = null;
        for (UnifiedMessage message : request.messages()) {
            if (message == null) {
                continue;
            }
            if ("system".equals(message.role())) {
                system = textOnly(message.content(), "system");
                continue;
            }
            ObjectNode item = messages.addObject();
            if (message.role() != null) {
                item.put("role", message.role());
            }
            item.put("content", textOnly(message.content(), "messages.content"));
        }

        if (system != null) {
            root.put("system", system);
        }

        request.options().forEach(root::set);
        return root;
    }

    @Override
    public UnifiedResponse parseResponse(String rawBody) {
        try {
            JsonNode root = objectMapper.readTree(rawBody == null ? "{}" : rawBody);
            JsonNode usageNode = root.path("usage");
            int inputTokens = usageNode.path("input_tokens").asInt(0);
            int outputTokens = usageNode.path("output_tokens").asInt(0);
            UnifiedUsage usage = new UnifiedUsage(inputTokens, outputTokens, 0, 0, inputTokens + outputTokens);

            JsonNode primaryContent = null;
            JsonNode content = root.path("content");
            if (content.isArray() && !content.isEmpty()) {
                primaryContent = TextNode.valueOf(textValue(content.get(0).get("text")) == null ? "" : textValue(content.get(0).get("text")));
            }

            return new UnifiedResponse(textValue(root.get("model")), usage, primaryContent, root);
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid upstream response body", exception);
        }
    }

    private String textOnly(JsonNode content, String fieldName) {
        if (content == null || content.isNull()) {
            return "";
        }
        if (content.isTextual()) {
            return content.asText();
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, fieldName + " must be text-only");
    }

    private String textValue(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }
}
