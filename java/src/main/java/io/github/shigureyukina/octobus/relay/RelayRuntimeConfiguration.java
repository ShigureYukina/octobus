package io.github.shigureyukina.octobus.relay;

import io.github.shigureyukina.octobus.config.RelayProperties;
import io.github.shigureyukina.octobus.relay.breaker.CircuitBreaker;
import io.github.shigureyukina.octobus.relay.breaker.InMemoryCircuitBreaker;
import io.github.shigureyukina.octobus.relay.session.InMemoryStickyStore;
import io.github.shigureyukina.octobus.relay.session.StickyStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Relay runtime beans shared across M3 relay tasks.
 */
@Configuration
public class RelayRuntimeConfiguration {

    @Bean
    public StickyStore stickyStore() {
        return new InMemoryStickyStore();
    }

    @Bean
    public CircuitBreaker circuitBreaker(RelayProperties relayProperties) {
        return new InMemoryCircuitBreaker(relayProperties.circuitBreaker());
    }
}
