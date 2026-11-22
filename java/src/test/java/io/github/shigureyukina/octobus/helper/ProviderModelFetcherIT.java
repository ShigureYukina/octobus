package io.github.shigureyukina.octobus.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.shigureyukina.octobus.common.ApiException;
import io.github.shigureyukina.octobus.domain.BaseUrl;
import io.github.shigureyukina.octobus.domain.Channel;
import io.github.shigureyukina.octobus.domain.ChannelKey;
import io.github.shigureyukina.octobus.relay.it.StubUpstream;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Integration test for {@link ProviderModelFetcher} using an embedded stub upstream.
 */
@SpringBootTest(
    properties = "octobus.auth.jwt-secret=test-secret"
)
class ProviderModelFetcherIT {

    private static StubUpstream stub;

    @Autowired
    private ProviderModelFetcher fetcher;

    @BeforeAll
    static void startStub() {
        stub = new StubUpstream();
    }

    @AfterAll
    static void stopStub() {
        stub.close();
    }

    @BeforeEach
    void resetStub() {
        stub.reset();
    }

    @Test
    void fetchOpenAIModels() {
        stub.enqueue(200, """
            {"data":[{"id":"gpt-4"},{"id":"gpt-4-turbo"}]}
            """);

        var channel = createChannel(stub.baseUrl());
        var models = fetcher.fetchModels(channel);

        assertThat(models).containsExactly("gpt-4", "gpt-4-turbo");
    }

    @Test
    void fetchAnthropicModels() {
        stub.enqueue(200, """
            {"data":[{"id":"claude-3-opus"},{"id":"claude-3-sonnet"}],"has_more":false}
            """);

        var channel = createAnthropicChannel(stub.baseUrl());
        var models = fetcher.fetchModels(channel);

        assertThat(models).containsExactly("claude-3-opus", "claude-3-sonnet");
    }

    @Test
    void fetchAnthropicModelsWithPagination() {
        // Page 1
        stub.enqueue(200, """
            {"data":[{"id":"claude-3-opus"}],"has_more":true,"last_id":"claude-3-opus"}
            """);
        // Page 2
        stub.enqueue(200, """
            {"data":[{"id":"claude-3-sonnet"}],"has_more":false}
            """);

        var channel = createAnthropicChannel(stub.baseUrl());
        var models = fetcher.fetchModels(channel);

        assertThat(models).containsExactly("claude-3-opus", "claude-3-sonnet");
    }

    @Test
    void matchRegexFiltersModels() {
        stub.enqueue(200, """
            {"data":[{"id":"gpt-4"},{"id":"gpt-4-turbo"},{"id":"gpt-3.5-turbo"},{"id":"davinci"}]}
            """);

        var channel = new Channel(
            0, "test", "openai/chat_completions", true,
            List.of(new BaseUrl(stub.baseUrl(), 0)),
            List.of(new ChannelKey(null, null, true, "sk-test", null, null, null, null)),
            null, null, false, false, 0, null, null, null,
            "gpt-4.*", null
        );
        var models = fetcher.fetchModels(channel);

        assertThat(models).containsExactly("gpt-4", "gpt-4-turbo");
    }

    @Test
    void upstreamErrorThrowsApiException() {
        stub.enqueue(500, "{\"error\":\"internal error\"}");

        var channel = createChannel(stub.baseUrl());

        assertThatThrownBy(() -> fetcher.fetchModels(channel))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void emptyBaseUrlsThrowsApiException() {
        var channel = new Channel(
            0, "test", "openai/chat_completions", true,
            List.of(), List.of(), null, null, false, false, 0, null, null, null, null, null
        );

        assertThatThrownBy(() -> fetcher.fetchModels(channel))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void noEnabledKeysThrowsApiException() {
        var channel = new Channel(
            0, "test", "openai/chat_completions", true,
            List.of(new BaseUrl("http://localhost:9999", 0)),
            List.of(), null, null, false, false, 0, null, null, null, null, null
        );

        assertThatThrownBy(() -> fetcher.fetchModels(channel))
            .isInstanceOf(ApiException.class);
    }

    private static Channel createChannel(String baseUrl) {
        return new Channel(
            0, "test", "openai/chat_completions", true,
            List.of(new BaseUrl(baseUrl, 0)),
            List.of(new ChannelKey(null, null, true, "sk-test", null, null, null, null)),
            null, null, false, false, 0, null, null, null, null, null
        );
    }

    private static Channel createAnthropicChannel(String baseUrl) {
        return new Channel(
            0, "anthropic-test", "anthropic/messages", true,
            List.of(new BaseUrl(baseUrl, 0)),
            List.of(new ChannelKey(null, null, true, "sk-ant-test", null, null, null, null)),
            null, null, false, false, 0, null, null, null, null, null
        );
    }
}
