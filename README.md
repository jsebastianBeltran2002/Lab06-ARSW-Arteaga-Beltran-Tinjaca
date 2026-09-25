# ARSW Collaborative Architecture Board — Lab 06

Backend foundation (Lab #4), an interactive SVG web client (Lab #5) and real-time collaboration over WebSocket/STOMP (Lab #6) for the ARSW Collaborative Architecture Board.

The goal was **not** to practice REST syntax, nor to build a polished frontend. The goal was a backend with explicit architectural boundaries, dependency inversion, constructor injection, consistent error handling, tests, and architecture evidence — and, on top of it, a thin, decoupled web client ready to receive WebSockets in Lab #6. See [`docs/ADR-002-client-boundaries.md`](docs/ADR-002-client-boundaries.md) for how the client is split into modules.

## Real-time collaboration (Lab 06)

Two or more browsers that load the same `boardId` share a live session. REST still creates/loads the Board and serves snapshots; STOMP/WebSocket carries the interaction events. See [`docs/event-contract.md`](docs/event-contract.md) and [`docs/ADR-003-rest-vs-realtime.md`](docs/ADR-003-rest-vs-realtime.md).

```text
Browser ── SEND /app/boards/{id}/events ──► BoardWebSocketController ──► BoardEventApplicationService ──► BoardRepository
   ▲                                                  │ (only if accepted)
   └──────────── /topic/boards/{id} ◄─────────────────┘
```

**Demo (three windows):**

1. Window A: **New Board** (the URL becomes `?board=<id>`), then **Connect live**.
2. Window B: open the same URL (or paste the id and **Load**), then **Connect live**.
3. Window C: create a *different* board and **Connect live**.
4. In A add a rectangle → it appears in B. In B drag it → A updates when the drag ends.
5. Create two elements and **Connect** them; **Edit Text**; **Delete Selected** on an endpoint → the connector disappears in both.
6. C never changes. Reload A → the board is recovered from the REST snapshot.

While live, a change is applied when the server broadcasts it back as accepted; a rejected change is reported only to its sender. Offline (not connected), the client behaves as in Lab 05: changes stay local until **Save**.

Module additions: `static/js/realtime/board-realtime-client.js` (the only module that knows STOMP) and `static/js/events/board-event.js` (event factory). Backend additions: `application/event`, `application/service/BoardEventApplicationService`, `infrastructure/web/ws`.

## Interactive client (Lab 05)

Open `http://localhost:8080/` after `mvn spring-boot:run` for the SVG board UI: create/load a `Board` by id, add `RECTANGLE`/`TEXT` elements by clicking the canvas, select and drag elements to move them, use **Connect** to draw a `CONNECTOR` between two elements, **Delete Selected** to remove one, and **Save** to persist the whole board via `PUT /api/boards/{boardId}`. Loading/saving show a status banner with a **Retry** action on failure.

Client modules (ES Modules, no framework, no bundler):

| Path | Responsibility |
|---|---|
| `static/js/api/board-api-client.js` | The only module allowed to call `fetch`; translates HTTP errors into a client error contract. |
| `static/js/state/board-state.js` | Local state (current board, selection, interaction mode, remote status) and pure list operations. |
| `static/js/ui/board-view.js` | Renders the state as SVG and turns mouse events into semantic callbacks. |
| `static/js/realtime/board-realtime-client.js` | (Lab 06) The only module that knows STOMP: connection, subscriptions, publication. |
| `static/js/events/board-event.js` | (Lab 06) Builds `BoardEvent` envelopes. |
| `static/js/app.js` | Orchestrates the modules above. |

## Technology baseline

- Java 21
- Spring Boot 3.x
- Maven
- In-memory persistence for this lab

## Target architecture

```text
REST Controller            STOMP Controller (Lab 06)
      |                           |
      v                           v
BoardApplicationService    BoardEventApplicationService
      \                          /
       v                        v
        BoardRepository (port)
                 |
                 v
     InMemoryBoardRepository (adapter)
```

## What this backend provides

- Domain types with invariants: `Board`, `BoardElement`, `ElementType` (`domain/model`, no HTTP/persistence dependencies).
- Output port `BoardRepository` and its in-memory adapter `InMemoryBoardRepository`.
- `BoardApplicationService`: create / get / replace / delete use cases, depending only on the `BoardRepository` port (constructor injection).
- Thin `BoardRestController` exposing `POST /api/boards`, `GET /api/boards/{boardId}`, `PUT /api/boards/{boardId}`, `DELETE /api/boards/{boardId}`.
- `GlobalExceptionHandler`: uniform `ApiError` contract for not-found boards, invalid requests, invalid domain input, and any unexpected error (no stack traces or internal messages ever leak to the client).
- Unit tests for the domain model invariants, the in-memory adapter, `BoardNotFoundException`, the application service, and MockMvc tests for the REST contract, including the board-not-found and invalid-element cases.

See `docs/api-contract.md` for the full REST contract, `docs/ADR-001-repository-boundary.md` for the repository-boundary decision, and `docs/architecture/` for the ArchiMate application view and class diagram.

## Run

```bash
mvn spring-boot:run
```

The app serves a small landing page at:

```text
http://localhost:8080/
```

## Try the API

```bash
curl -X POST http://localhost:8080/api/boards -H "Content-Type: application/json" -d "{\"name\":\"Architecture Session\"}"

curl http://localhost:8080/api/boards/{boardId}

curl -X PUT http://localhost:8080/api/boards/{boardId} -H "Content-Type: application/json" -d "{\"name\":\"Renamed\",\"elements\":[]}"

curl -X DELETE http://localhost:8080/api/boards/{boardId}
```

Replace `{boardId}` with the `id` returned by the `POST` call.

> **Windows PowerShell note:** `curl` there is an alias for `Invoke-WebRequest`, and even `curl.exe` mis-parses inline `-d "{...}"` JSON with escaped quotes (PowerShell's native-command argument passing mangles them). Use the automated script below instead of typing these by hand on Windows.

## Demo script (recommended way to verify end to end)

With the app running (`mvn spring-boot:run`), run the matching script from the project root in a second terminal. It creates a board, reads it, replaces it, and hits both documented error cases (`404 BOARD_NOT_FOUND` and `400 INVALID_INPUT`) — printing every response so you can see the full contract working live.

```powershell
# Windows PowerShell
.\scripts\demo.ps1
```

```bash
# macOS / Linux / Git Bash
bash scripts/demo.sh
```

Expected output: five labeled steps, ending in `DEMO COMPLETE`, with a generated `id`, a `200` on read/replace, a `404` for a missing board, and a `400` (never a raw `500`) for an invalid element.

## Verify

```bash
mvn test
```

All tests pass — 65 tests: domain model, in-memory adapter, application services, REST controller (MockMvc), the Lab 06 event contract and event service, the STOMP controller, and an integration test with real STOMP clients that checks propagation within a board, isolation between boards and private rejections.

## Continuity rule

This repository accumulates Labs #4–#6 and is the baseline for **Lab 07 — Concurrent Collaboration**. The live path (`BoardEventApplicationService.apply`: find → transition → save over a `HashMap`) is intentionally not thread-safe and has no event ordering; Lab 07 will stress it with simultaneous clients. See `NEXT_LABS.md`.
