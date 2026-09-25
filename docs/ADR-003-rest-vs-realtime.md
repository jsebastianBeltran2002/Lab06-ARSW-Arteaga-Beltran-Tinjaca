# ADR-003 — REST and WebSocket/STOMP coexist

## Status
Accepted (Lab #6)

## Context
Up to Lab #5 the client talked to the backend only through REST (`/api/boards`): create, load, and save the full Board with `PUT` when the user pressed **Save**. Lab #6 requires two or more browsers on the same `boardId` to see each other's changes (create, move, connect, delete) without reloading and without polling, and each board must be an isolated session.

REST alone cannot push a change to the other participants; making every client poll `GET /api/boards/{id}` would add latency and load and would still transfer the whole Board for every tiny change. On the other hand, replacing REST with WebSocket for everything would force a new client to receive the initial state as a message stream and would break the existing, tested REST contract.

## Decision
Keep both interaction styles, each for what it is good at:

- **REST (HTTP/JSON)** for *resources*: create a Board, load its initial state, recover a snapshot after a reload, and explicit full saves. Unchanged from Lab #5 (`BoardRestController` → `BoardApplicationService`).
- **STOMP over WebSocket** for *interaction events* inside a live session: `ELEMENT_CREATED`, `ELEMENT_MOVED`, `ELEMENT_UPDATED`, `ELEMENT_DELETED`, `CONNECTOR_CREATED` ([event contract](event-contract.md)). Clients `SEND /app/boards/{id}/events`; `BoardWebSocketController` hands the event to `BoardEventApplicationService`, which validates it, applies it to the Board and saves it through the same `BoardRepository` port; only then is it broadcast on `/topic/boards/{id}`.

Both paths share the domain model and the repository port, so the live state and the REST snapshot are the same state. A client first loads the Board by REST, then subscribes to the live topic (and re-reads the snapshot right after subscribing so it does not miss changes accepted in between).

### Comparison

| Aspect | REST | WebSocket/STOMP |
|---|---|---|
| Interaction style | Request/response, initiated by the client; the caller gets an immediate status code. | Asynchronous notification; the server pushes to every subscriber of a topic. |
| Unit of information | A resource **snapshot** (the whole Board). | An **interaction event** (the minimum change: one element, one position). |
| Client coupling | Client only needs to know resources and verbs; stateless. | Client keeps a connection and a subscription, and must turn events into state transitions (`BoardState.applyEvent`). |
| Failure / reconnection | Each request fails on its own; retry is trivial (the **Retry** button). | A dropped socket loses every event sent while disconnected; the client must reconnect *and* re-read the snapshot by REST. |
| Effect on the Board contract | `Board`/`BoardElement` JSON, unchanged. | New `BoardEvent` envelope that *references* `BoardElement` but is not a domain entity. |

## Consequences

**Benefits**
- Real-time propagation without polling, isolated per `boardId` by the topic name.
- The REST API, its tests and its error contract stay intact; reloading a page or opening a board late still works through a plain `GET`.
- Events stay small and explicit (a move sends `elementId`, `x`, `y`, not the whole Board), and the same `BoardEventApplicationService` is the single place where live changes are validated.
- The domain stays free of protocol concerns: `Board` only gained pure transitions (`withElementAdded/Moved/Replaced/Removed`); STOMP types live only in `infrastructure/web/ws`.

**Costs**
- Two interfaces to document, test and secure (`api-contract.md` + `event-contract.md`; MockMvc tests + STOMP tests).
- More client complexity: a connection lifecycle (`connecting/connected/error/disconnected`), a second error channel (`/user/queue/errors`), and the rule that the STOMP callback may only update `BoardState`.
- Two ways to change a Board coexist: while live, a `PUT` (Save) overwrites the Board without notifying other participants.

**Limitations accepted for the course scope**
- Simple in-memory broker: works only with a single server instance; no external broker (RabbitMQ, Redis, Kafka).
- No sequence numbers, versions or conflict resolution; concurrent edits may produce lost updates. Deferred on purpose to Lab #7.
- No authentication: `actorId` is informational and chosen by the client.
- Events sent while a client is disconnected are not replayed; recovery is by REST snapshot.
