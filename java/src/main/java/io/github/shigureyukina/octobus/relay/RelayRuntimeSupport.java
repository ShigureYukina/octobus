package io.github.shigureyukina.octobus.relay;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.shigureyukina.octobus.config.RelayProperties;
import io.github.shigureyukina.octobus.persistence.APIKeyRepository;
import io.github.shigureyukina.octobus.persistence.ChannelRepository;
import io.github.shigureyukina.octobus.persistence.GroupRepository;
import io.github.shigureyukina.octobus.relay.breaker.CircuitBreaker;
import io.github.shigureyukina.octobus.relay.metrics.RelayMetricsRecorder;
import io.github.shigureyukina.octobus.relay.provider.anthropic.AnthropicProviderClient;
import io.github.shigureyukina.octobus.relay.provider.gemini.GeminiProviderClient;
import io.github.shigureyukina.octobus.relay.provider.openai.OpenAICompatibleProviderClient;
import io.github.shigureyukina.octobus.relay.session.StickyStore;
import io.github.shigureyukina.octobus.relay.spi.TransformerRegistry;
import org.springframework.stereotype.Component;

/**
 * Relay runtime dependencies shared by the OpenAI Chat main path.
 */
@Component
public class RelayRuntimeSupport {

    private final ObjectMapper objectMapper;
    private final APIKeyRepository apiKeyRepository;
    private final GroupRepository groupRepository;
    private final ChannelRepository channelRepository;
    private final StickyStore stickyStore;
    private final CircuitBreaker circuitBreaker;
    private final OpenAICompatibleProviderClient openAICompatibleProviderClient;
    private final AnthropicProviderClient anthropicProviderClient;
    private final GeminiProviderClient geminiProviderClient;
    private final RelayProperties relayProperties;
    private final TransformerRegistry transformerRegistry;
    private final RelayMetricsRecorder relayMetricsRecorder;

    public RelayRuntimeSupport(
        ObjectMapper objectMapper,
        APIKeyRepository apiKeyRepository,
        GroupRepository groupRepository,
        ChannelRepository channelRepository,
        StickyStore stickyStore,
        CircuitBreaker circuitBreaker,
        OpenAICompatibleProviderClient openAICompatibleProviderClient,
        AnthropicProviderClient anthropicProviderClient,
        GeminiProviderClient geminiProviderClient,
        RelayProperties relayProperties,
        RelayMetricsRecorder relayMetricsRecorder
    ) {
        this.objectMapper = objectMapper;
        this.apiKeyRepository = apiKeyRepository;
        this.groupRepository = groupRepository;
        this.channelRepository = channelRepository;
        this.stickyStore = stickyStore;
        this.circuitBreaker = circuitBreaker;
        this.openAICompatibleProviderClient = openAICompatibleProviderClient;
        this.anthropicProviderClient = anthropicProviderClient;
        this.geminiProviderClient = geminiProviderClient;
        this.relayProperties = relayProperties;
        this.transformerRegistry = TransformerRegistry.defaultRegistry(objectMapper);
        this.relayMetricsRecorder = relayMetricsRecorder;
    }

    public ObjectMapper objectMapper() {
        return objectMapper;
    }

    public APIKeyRepository apiKeyRepository() {
        return apiKeyRepository;
    }

    public GroupRepository groupRepository() {
        return groupRepository;
    }

    public ChannelRepository channelRepository() {
        return channelRepository;
    }

    public StickyStore stickyStore() {
        return stickyStore;
    }

    public CircuitBreaker circuitBreaker() {
        return circuitBreaker;
    }

    public OpenAICompatibleProviderClient openAICompatibleProviderClient() {
        return openAICompatibleProviderClient;
    }

    public AnthropicProviderClient anthropicProviderClient() {
        return anthropicProviderClient;
    }

    public GeminiProviderClient geminiProviderClient() {
        return geminiProviderClient;
    }

    public RelayProperties relayProperties() {
        return relayProperties;
    }

    public TransformerRegistry transformerRegistry() {
        return transformerRegistry;
    }

    public RelayMetricsRecorder relayMetricsRecorder() {
        return relayMetricsRecorder;
    }
}
