# ARSW Collaborative Architecture Board — Lab 05

Backend foundation (Lab #4) plus an interactive SVG web client (Lab #5) for the ARSW Collaborative Architecture Board.

The goal was **not** to practice REST syntax, nor to build a polished frontend. The goal was a backend with explicit architectural boundaries, dependency inversion, constructor injection, consistent error handling, tests, and architecture evidence — and, on top of it, a thin, decoupled web client ready to receive WebSockets in Lab #6. See [`docs/ADR-002-client-boundaries.md`](docs/ADR-002-client-boundaries.md) for how the client is split into modules.

## Interactive client (Lab 05)

Open `http://localhost:8080/` after `mvn spring-boot:run` for the SVG board UI: create/load a `Board` by id, add `RECTANGLE`/`TEXT` elements by clicking the canvas, select and drag elements to move them, use **Connect** to draw a `CONNECTOR` between two elements, **Delete Selected** to remove one, and **Save** to persist the whole board via `PUT /api/boards/{boardId}`. Loading/saving show a status banner with a **Retry** action on failure.

Client modules (ES Modules, no framework, no bundler):

| Path | Responsibility |
|---|---|
| `static/js/api/board-api-client.js` | The only module allowed to call `fetch`; translates HTTP errors into a client error contract. |
| `static/js/state/board-state.js` | Local state (current board, selection, interaction mode, remote status) and pure list operations. |
| `static/js/ui/board-view.js` | Renders the state as SVG and turns mouse events into semantic callbacks. |
| `static/js/app.js` | Orchestrates the three modules above. |

## Technology baseline

- Java 21
- Spring Boot 3.x
- Maven
- In-memory persistence for this lab

## Target architecture

```text
REST Controller
      |
      v
Application Service
      |
      v
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

All tests (domain model, in-memory adapter, application service, and REST controller) pass — 39 tests, including the board-not-found, invalid-element, delete, and Lab 05 `CONNECTOR`-invariant cases.

## Continuity rule

This completed Lab 05 repository becomes the conceptual baseline for **Lab 06 — Real-Time Collaboration** (WebSocket/STOMP). Avoid unnecessary changes to contracts and package boundaries.
