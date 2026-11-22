package io.github.shigureyukina.octobus.task;

import io.github.shigureyukina.octobus.domain.BaseUrl;
import io.github.shigureyukina.octobus.domain.Channel;
import io.github.shigureyukina.octobus.persistence.ChannelRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ChannelDelayTask {

    private static final Logger log = LoggerFactory.getLogger(ChannelDelayTask.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final ChannelRepository channelRepository;
    private final HttpClient httpClient;

    public ChannelDelayTask(ChannelRepository channelRepository) {
        this.channelRepository = channelRepository;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
    }

    @Scheduled(fixedRate = 3_600_000) // every hour
    public void checkDelays() {
        List<Channel> channels = channelRepository.findAll();
        for (Channel channel : channels) {
            if (channel.baseUrls() == null || channel.baseUrls().isEmpty()) {
                continue;
            }
            List<BaseUrl> updatedUrls = new ArrayList<>();
            boolean changed = false;
            for (BaseUrl baseUrl : channel.baseUrls()) {
                if (baseUrl == null || baseUrl.url() == null || baseUrl.url().isBlank()) {
                    updatedUrls.add(baseUrl);
                    continue;
                }
                int delayMs = measureDelay(baseUrl.url());
                updatedUrls.add(new BaseUrl(baseUrl.url(), delayMs));
                changed = true;
            }
            if (changed) {
                channelRepository.updateBaseUrls(channel.id(), updatedUrls);
            }
        }
        log.info("Checked delays for {} channels", channels.size());
    }

    private int measureDelay(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            Instant start = Instant.now();
            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            long ms = Duration.between(start, Instant.now()).toMillis();
            return (int) ms;
        } catch (Exception exception) {
            log.debug("Delay check failed for {}: {}", url, exception.getMessage());
            return -1;
        }
    }
}
