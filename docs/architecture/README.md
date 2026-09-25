# Architecture Evidence — Lab 06

1. **[ArchiMate Application View](application-view.md)** — Web Client (with `BoardRealtimeClient`), REST interface (**HTTP/JSON**), WebSocket/STOMP interface (**STOMP/WebSocket**), `BoardApplicationService` and `BoardEventApplicationService`, the in-memory broker and the repository port/adapter, plus a table of who publishes and who subscribes to each destination.

2. **[Class / module diagram](class-diagram.md)** — `BoardEvent`, `BoardEventType`, `BoardEventPayload`, `BoardEventApplicationService`, `BoardWebSocketController`, `WebSocketConfig` and their relation to the existing `Board`/`BoardElement` model and `BoardRepository` port, plus the client modules.

See also:
- **[Event contract](../event-contract.md)** — destinations, envelope, per-type payload and rejections.
- **[ADR-003 — REST and WebSocket/STOMP coexist](../ADR-003-rest-vs-realtime.md)**.
- **[ADR-002 — Client boundaries](../ADR-002-client-boundaries.md)** and **[ADR-001 — Repository boundary](../ADR-001-repository-boundary.md)** from previous labs.

## Quality rule

The diagrams describe the code actually delivered in this repository (see `src/main/java/edu/eci/arsw/collabboard` and `src/main/resources/static/js`) — no decorative boxes, no framework classes beyond what carries architectural meaning.
