/**
 * Relay protocol adapters, balancing, and streaming logic.
 *
 * <p>T1 delivers the non-streaming relay skeleton:
 * <ul>
 *   <li>{@link io.github.shigureyukina.octobus.relay.RelayHandler} –
 *       WebFlux handler factory parameterized by inbound format.</li>
 *   <li>{@link io.github.shigureyukina.octobus.relay.RelayRun} –
 *       request-level state holder.</li>
 *   <li>{@link io.github.shigureyukina.octobus.relay.RelayAttempt} –
 *       single-candidate attempt scaffold.</li>
 *   <li>{@link io.github.shigureyukina.octobus.relay.RelayRouter} –
 *       registers all seven {@code /v1/*} routes.</li>
 * </ul>
 *
 * <p>T2 adds the relay SPI under {@code relay.spi}, defining the
 * unified request/response model and guarded transformer registry.
 * Later M3 sub-tasks will populate the real candidate iterator, the
 * breaker, sticky sessions, provider clients, and metrics recording.
 */
package io.github.shigureyukina.octobus.relay;
