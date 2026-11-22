package io.github.shigureyukina.octobus.relay.spi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.shigureyukina.octobus.common.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Minimal OpenAI-compatible outbound transformer used to anchor the T2 registry.
 */
public final class OpenAIChatOutboundTransformer implements OutboundTransformer {

    private final ObjectMapper objectMapper;

    public OpenAIChatOutboundTransformer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public RelayProviderFormat providerFormat() {
        return RelayProviderFormat.OPENAI_CHAT_COMPLETION;
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
            throw new ApiException(HttpStatus.BAD_REQUEST, "OpenAI Chat outbound only supports CHAT requests");
        }

        ObjectNode root = objectMapper.createObjectNode();
        if (request.model() != null) {
            root.put("model", request.model());
        }
        root.put("stream", request.stream());

        ArrayNode messages = root.putArray("messages");
        for (UnifiedMessage message : request.messages()) {
            ObjectNode item = messages.addObject();
            if (message.role() != null) {
                item.put("role", message.role());
            }
            if (message.content() != null) {
                item.set("content", message.content());
            }
        }

        if (!request.tools().isEmpty()) {
            ArrayNode tools = root.putArray("tools");
            for (UnifiedTool tool : request.tools()) {
                ObjectNode item = tools.addObject();
                item.put("type", "function");
                ObjectNode function = item.putObject("function");
                if (tool.name() != null) {
                    function.put("name", tool.name());
                }
                if (tool.description() != null) {
                    function.put("description", tool.description());
                }
                if (tool.schema() != null) {
                    function.set("parameters", tool.schema());
                }
            }
        }

        request.options().forEach(root::set);
        return root;
    }

    @Override
    public UnifiedResponse parseResponse(String rawBody) {
        try {
            JsonNode root = objectMapper.readTree(rawBody == null ? "{}" : rawBody);
            JsonNode usageNode = root.path("usage");
            int cacheRead = 0;
            int cacheWrite = 0;
            JsonNode details = usageNode.path("prompt_tokens_details");
            if (!details.isMissingNode() && !details.isNull()) {
                cacheRead = Math.max(0, details.path("cached_tokens").asInt(0));
                cacheWrite = Math.max(0, details.path("write_cached_tokens").asInt(0));
            }
            UnifiedUsage usage = new UnifiedUsage(
                usageNode.path("prompt_tokens").asInt(0),
                usageNode.path("completion_tokens").asInt(0),
                cacheRead,
                cacheWrite,
                usageNode.path("total_tokens").asInt(0)
            );
            JsonNode primaryContent = root.path("choices").isArray() && root.path("choices").size() > 0
                ? root.path("choices").get(0)
                : null;

            return new UnifiedResponse(
                textValue(root.get("model")),
                usage,
                primaryContent,
                root
            );
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid upstream response body", exception);
        }
    }

    private String textValue(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }
}
