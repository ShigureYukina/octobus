package io.github.shigureyukina.octobus.relay.spi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.shigureyukina.octobus.common.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Serializes a unified relay request into the OpenAI Embeddings wire format
 * and parses the upstream response.
 *
 * <p>MVP scope: serializes model and input; parsing extracts model and
 * usage from the response body.
 */
public final class OpenAIEmbeddingsOutboundTransformer implements OutboundTransformer {

    private final ObjectMapper objectMapper;

    public OpenAIEmbeddingsOutboundTransformer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public RelayProviderFormat providerFormat() {
        return RelayProviderFormat.OPENAI_EMBEDDING;
    }

    @Override
    public boolean supports(UnifiedRequestType requestType) {
        return requestType == UnifiedRequestType.EMBEDDING;
    }

    @Override
    public JsonNode serializeRequest(UnifiedRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unified request is required");
        }

        ObjectNode root = objectMapper.createObjectNode();
        if (request.model() != null) {
            root.put("model", request.model());
        }

        request.options().forEach(root::set);
        return root;
    }

    @Override
    public UnifiedResponse parseResponse(String rawBody) {
        try {
            JsonNode root = objectMapper.readTree(rawBody == null ? "{}" : rawBody);
            JsonNode usageNode = root.path("usage");
            UnifiedUsage usage = new UnifiedUsage(
                usageNode.path("prompt_tokens").asInt(0),
                0,
                0,
                0,
                usageNode.path("total_tokens").asInt(0)
            );
            return new UnifiedResponse(
                textValue(root.get("model")),
                usage,
                root,
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
