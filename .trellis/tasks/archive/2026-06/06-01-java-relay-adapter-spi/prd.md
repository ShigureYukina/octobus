# Java relay adapter SPI

## Goal

Define the internal relay transformation contract for Milestone 3: a unified
request/response model, inbound and outbound transformer interfaces, and a
registry that resolves supported format pairs.

After this task the relay runtime has a stable SPI boundary. OpenAI Chat maps
to a real registry cell, while the other inbound formats are registered behind
predictable guarded "not supported" behavior until their implementations land.

## Dependency Notes

- Depends on `06-01-java-relay-core`: `RelayHandler` / `RelayRun` / route
  skeleton already exist and must remain the integration surface.
- Implements T2 from parent task
  `06-01-05-31-java-rewrite-milestone-3/implement.md`.
- Unblocks `06-01-java-relay-openai-chat` directly and provides type contracts
  consumed later by balancer, metrics, and integration work.
- Must follow `.trellis/spec/java/relay-protocol-adapter.md` and keep HTTP I/O
  out of this task.

## Requirements

- Add unified relay model types under `relay/spi/` for request, response,
  usage, message, and tool payloads.
- Define `InboundTransformer` and `OutboundTransformer` interfaces that
  normalize client formats into the unified model and serialize back out.
- Add a registry/factory that resolves transformer pairs from inbound format
  and outbound/provider target metadata.
- Register the matrix from `.trellis/spec/java/relay-protocol-adapter.md`.
- Ensure the OpenAI Chat cell resolves to real transformer implementations or
  placeholders ready for T6 wiring.
- Ensure non-target formats return a controlled guarded transformer or an
  equivalent predictable "not supported" outcome rather than nulls or runtime
  crashes.
- Keep this task pure transformation plus metadata. No `WebClient`, no
  provider HTTP calls, no persistence, no candidate selection.
- Keep compatibility with the T1 relay skeleton so `RelayRun` can depend on the
  SPI without changing route behavior yet.

## Acceptance Criteria

- [ ] `mvn -pl java -am test` passes for the new `relay/spi` code.
- [ ] Unified request/response model types compile and are usable from relay
      runtime code.
- [ ] Registry tests cover the supported inbound-by-format matrix.
- [ ] OpenAI Chat resolves through the registry successfully.
- [ ] OpenAI Responses and other non-target formats return a predictable
      guarded outcome instead of failing with missing bean / null errors.
- [ ] No provider network code, persistence code, or balancer logic is added
      in this task.
