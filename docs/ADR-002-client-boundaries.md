# ADR-002 — Client Boundaries

## Status
Accepted

## Context
Lab #5 adds an interactive SVG web client on top of the existing `/api/boards` REST contract, without introducing a framework (no React/Vue/Angular) and without touching the backend's package boundaries established in [[ADR-001-repository-boundary]]. The lab statement (`ARSW_2026-2_Lab_05_Interactive_Board_ECI_Entrega.pdf`, §3) asks for responsibilities equivalent to `BoardApiClient`, `BoardState`, `BoardView`/SVG rendering and a `BoardApp` orchestrator, with dependencies free of cycles, and explicitly requires that Lab #6 (WebSocket/STOMP real-time collaboration) can be layered in afterward without a client rewrite.

Left unconstrained, a small vanilla-JS client tends to collapse these responsibilities into one file: DOM event handlers call `fetch` directly, mutate global variables, and re-render inline. That works for a demo but makes two things hard: (1) knowing what "the current board" is at any point in time (the DOM becomes the source of truth), and (2) later replacing the transport (REST → WebSocket push) without rewriting rendering and interaction code.

## Decision
Split the client into four ES modules with a single allowed dependency direction, mirroring the backend's controller → service → port shape:

- **`js/api/board-api-client.js`** (`BoardApiClient`) — the only module allowed to call `fetch`. It knows HTTP verbs, JSON encoding, and the backend's `ApiError` shape; it translates any non-2xx response into an `ApiClientError` with `status`/`code`/`message`. It has zero knowledge of the DOM, SVG, or `BoardState`.
- **`js/state/board-state.js`** (`BoardState`) — the single source of truth. Holds the current `board`, `selectedElementId`, `connectSourceId`, interaction `mode`, and `remoteStatus`/`errorMessage`. List mutations (`addElement`, `updateElementPosition`, `removeElement`) are exported as pure functions (input list in, new list out) so they're testable without a browser; `BoardState` wraps them with a minimal pub-sub (`subscribe`/`notify`) so the view re-renders on every change. It never imports `BoardApiClient` or touches `document`.
- **`js/ui/board-view.js`** (`BoardView`) — renders a given state object as SVG (`RECTANGLE`/`TEXT` as nodes, `CONNECTOR` as a line between the centers of its endpoints) and converts raw mouse events into three semantic callbacks (`onElementInteraction`, `onElementDragEnd`, `onCanvasInteraction`) supplied by its caller. It never imports `BoardState` or `BoardApiClient`, and it never calls `fetch`.
- **`js/app.js`** (`BoardApp`, the orchestrator) — the only module that imports all three of the above. It decides what a click means based on the current `mode` (add rectangle/text, pick a connector endpoint, select), drives `BoardApiClient` calls wrapped in a `loading → success|error` cycle, and pushes results into `BoardState`. `BoardState`'s `notify()` re-invokes `render()`, which updates both the SVG (`BoardView.render`) and the toolbar/status DOM.

This gives a one-directional dependency graph — `app.js → {board-api-client.js, board-state.js, board-view.js}`, with no edges between the three leaves — matching the target architecture in the lab statement without needing a framework or bundler.

## Consequences

**Positive**
- `BoardState`'s pure list operations (`addElement`/`updateElementPosition`/`removeElement`/`createConnector`) can be — and were — exercised directly in Node without a browser or DOM, the same way `BoardApplicationService` is unit-tested without a servlet container.
- Swapping the transport in Lab #6 (REST polling → WebSocket push) only touches `board-api-client.js` (or a sibling module) and the few `app.js` call sites that invoke it; `BoardState` and `BoardView` are unaware persistence happens over HTTP at all.
- `grep -rn "fetch(" src/main/resources/static/js` returns exactly one file (`board-api-client.js`), which is the acceptance criterion the rubric checks for ("No existe fetch fuera del módulo de acceso HTTP").
- Cascading deletes (removing a `CONNECTOR` when either endpoint is deleted) live in the pure `removeElement` function in `board-state.js`, keeping that consistency rule out of both the view and the backend.

**Negative / trade-off**
- No compile-time module boundary enforcement (no TypeScript, no bundler) — the "one-directional dependency" rule is a convention checked by code review and the `grep` above, not by the toolchain. Acceptable for a 4-day lab scoped to plain ES Modules; would need revisiting if the client grew significantly.
- `BoardApp` is a fairly large orchestrator (toolbar wiring, retry logic, mode-to-action dispatch) since it is intentionally the *only* place allowed to know about all three other modules. This is the same trade-off `BoardRestController`/`BoardApplicationService` make on the backend: one coordinating layer versus scattering coordination logic across leaves.

## Evidence
- `src/main/resources/static/js/api/board-api-client.js` — sole `fetch` caller; exports `ApiClientError`.
- `src/main/resources/static/js/state/board-state.js` — pure functions (`addElement`, `updateElementPosition`, `removeElement`) plus `BoardState` class; no `import` of `board-api-client.js` or DOM APIs.
- `src/main/resources/static/js/ui/board-view.js` — `BoardView` class; no `import` of `board-state.js` or `board-api-client.js`; communicates only through constructor-injected `handlers`.
- `src/main/resources/static/js/app.js` — the only file importing all three modules; owns the `loading`/`success`/`error` cycle and `retryLastOperation()`.
- Verified with `node --check` on all four modules and by exercising the pure `board-state.js` functions directly in Node (add/move/delete/cascade-delete-connector/connect-flow), plus an end-to-end `curl` smoke test (create → `PUT` with a `RECTANGLE`, `TEXT` and `CONNECTOR` → `GET` reload) confirming the payload `BoardApiClient.replaceBoard` sends is accepted and round-trips unchanged.

## Lab 06 follow-up
The split held: real-time collaboration was added as two new leaf modules — `js/realtime/board-realtime-client.js` (the only STOMP user, the WebSocket counterpart of `board-api-client.js`) and `js/events/board-event.js` (event factory) — plus `BoardState.applyEvent`. `BoardView` did not change. `app.js` remains the only module that wires everything; the STOMP callback only transitions `BoardState` and rendering follows from its `notify()`. See [ADR-003](ADR-003-rest-vs-realtime.md).
