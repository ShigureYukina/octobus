package io.github.shigureyukina.octobus.relay.spi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.github.shigureyukina.octobus.common.ApiException;
import org.springframework.http.HttpStatus;

public final class GeminiContentsOutboundTransformer implements OutboundTransformer {

    private final ObjectMapper objectMapper;

    public GeminiContentsOutboundTransformer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public RelayProviderFormat providerFormat() {
        return RelayProviderFormat.GEMINI_CONTENTS;
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
            throw new ApiException(HttpStatus.BAD_REQUEST, "Gemini outbound only supports CHAT requests");
        }
        if (request.model() == null || request.model().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Model is required for Gemini requests");
        }

        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", request.model());

        ArrayNode contents = root.putArray("contents");
        String systemInstruction = null;
        for (UnifiedMessage message : request.messages()) {
            if (message == null) {
                continue;
            }
            if ("system".equals(message.role())) {
                String text = textOnly(message.content(), "systemInstruction");
                if (!text.isBlank()) {
                    systemInstruction = systemInstruction == null ? text : systemInstruction + "\n" + text;
                }
                continue;
            }

            ObjectNode item = contents.addObject();
            item.put("role", toGeminiRole(message.role()));
            ArrayNode parts = item.putArray("parts");
            for (String partText : textParts(message.content(), "contents.parts")) {
                if (!partText.isBlank()) {
                    parts.addObject().put("text", partText);
                }
            }
            if (parts.isEmpty()) {
                parts.addObject().put("text", "");
            }
        }

        if (systemInstruction != null) {
            ObjectNode systemNode = root.putObject("systemInstruction");
            systemNode.putArray("parts").addObject().put("text", systemInstruction);
        }

        ObjectNode generationConfig = objectMapper.createObjectNode();
        copyOption(request, generationConfig, "temperature", "temperature");
        copyOption(request, generationConfig, "top_p", "topP");
        copyOption(request, generationConfig, "max_tokens", "maxOutputTokens");
        if (!generationConfig.isEmpty()) {
            root.set("generationConfig", generationConfig);
        }

        return root;
    }

    @Override
    public UnifiedResponse parseResponse(String rawBody) {
        try {
            JsonNode root = objectMapper.readTree(rawBody == null ? "{}" : rawBody);
            String model = textValue(root.get("modelVersion"));
            if (model == null) {
                model = textValue(root.get("model"));
            }

            JsonNode usageNode = root.path("usageMetadata");
            int inputTokens = usageNode.path("promptTokenCount").asInt(0);
            int outputTokens = usageNode.path("candidatesTokenCount").asInt(0);
            int totalTokens = usageNode.path("totalTokenCount").asInt(inputTokens + outputTokens);
            UnifiedUsage usage = new UnifiedUsage(inputTokens, outputTokens, 0, 0, totalTokens);

            String text = extractCandidateText(root);
            ObjectNode rawPayload = toOpenAiChatResponse(root, model, text, usage);
            return new UnifiedResponse(model, usage, TextNode.valueOf(text), rawPayload);
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid upstream response body", exception);
        }
    }

    private void copyOption(UnifiedRequest request, ObjectNode target, String sourceField, String targetField) {
        JsonNode value = request.options().get(sourceField);
        if (value != null && !value.isNull()) {
            target.set(targetField, value);
        }
    }

    private ObjectNode toOpenAiChatResponse(JsonNode root, String model, String text, UnifiedUsage usage) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("id", textValue(root.get("responseId")) == null ? "gemini-response" : textValue(root.get("responseId")));
        response.put("object", "chat.completion");
        response.put("created", java.time.Instant.now().getEpochSecond());
        response.put("model", model == null ? "gemini" : model);

        ArrayNode choices = response.putArray("choices");
        ObjectNode choice = choices.addObject();
        choice.put("index", 0);
        ObjectNode message = choice.putObject("message");
        message.put("role", "assistant");
        message.put("content", text);
        choice.put("finish_reason", mapFinishReason(root));

        ObjectNode usageNode = response.putObject("usage");
        usageNode.put("prompt_tokens", usage.inputTokens());
        usageNode.put("completion_tokens", usage.outputTokens());
        usageNode.put("total_tokens", usage.totalTokens());
        return response;
    }

    private String mapFinishReason(JsonNode root) {
        String finishReason = root.path("candidates").path(0).path("finishReason").asText("STOP");
        return switch (finishReason) {
            case "STOP" -> "stop";
            case "MAX_TOKENS" -> "length";
            case "SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII" -> "content_filter";
            default -> "stop";
        };
    }

    private String extractCandidateText(JsonNode root) {
        JsonNode parts = root.path("candidates").path(0).path("content").path("parts");
        if (!parts.isArray()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (JsonNode part : parts) {
            String text = textValue(part.get("text"));
            if (text != null) {
                builder.append(text);
            }
        }
        return builder.toString();
    }

    private String toGeminiRole(String role) {
        return "assistant".equals(role) ? "model" : "user";
    }

    private java.util.List<String> textParts(JsonNode content, String fieldName) {
        if (content == null || content.isNull()) {
            return java.util.List.of("");
        }
        if (content.isTextual()) {
            return java.util.List.of(content.asText());
        }
        if (content.isArray()) {
            java.util.List<String> parts = new java.util.ArrayList<>();
            for (JsonNode item : content) {
                String type = textValue(item.get("type"));
                if (type != null && !"text".equals(type)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, fieldName + " must be text-only");
                }
                String text = textValue(item.get("text"));
                if (text == null && item.has("content")) {
                    text = textValue(item.get("content"));
                }
                if (text != null) {
                    parts.add(text);
                }
            }
            return parts.isEmpty() ? java.util.List.of("") : java.util.List.copyOf(parts);
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, fieldName + " must be text-only");
    }

    private String textOnly(JsonNode content, String fieldName) {
        return String.join("\n", textParts(content, fieldName));
    }

    private String textValue(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }
}
