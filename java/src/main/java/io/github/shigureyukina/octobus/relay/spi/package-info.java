/**
 * Unified relay request/response models and transformer contracts.
 *
 * <p>T2 establishes the SPI boundary used by the relay runtime:
 * inbound client payloads normalize into one internal request model,
 * outbound provider adapters serialize from that model, and guarded
 * implementations provide predictable "not supported" behavior until
 * later M3 tasks wire real provider paths.
 */
package io.github.shigureyukina.octobus.relay.spi;
