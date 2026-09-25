# Incremental Evolution

- **Lab 04:** backend foundation — domain, REST API, repository port/adapter. *(done)*
- **Lab 05:** interactive SVG web client consuming the existing REST API. *(done)*
- **Lab 06:** real-time collaboration using WebSocket/STOMP and per-board topics. *(done)*
- **Lab 07:** concurrent session state, race-condition analysis, consistency strategy.
- **Lab 08:** architecture consolidation, trade-offs, diagrams and final evidence.

## Output of Lab 06 → input of Lab 07

Do not rebuild the application. Lab 07 will stress the same real-time path with simultaneous clients and analyze:

- non-thread-safe shared state (`InMemoryBoardRepository` uses a `HashMap`);
- the read-modify-write race in `BoardEventApplicationService.apply` (find → transition → save);
- lost updates;
- event ordering and stale writes (the event contract has no sequence number or version yet);
- an explicit consistency strategy.

Do not preemptively add CRDT, distributed locks or external brokers.
