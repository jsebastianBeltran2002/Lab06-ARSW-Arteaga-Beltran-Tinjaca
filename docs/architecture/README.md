# Architecture Evidence — Lab 05

1. **[ArchiMate Application View](application-view.md)** — Web Client (`BoardApp`, `BoardApiClient`, `BoardState`, SVG Board View), REST interface, application service, repository port/adapter, in-memory data object, and error handler, with the ArchiMate concept each element maps to.

2. **[Class / module diagram](class-diagram.md)** — `BoardRestController`, `BoardApplicationService`, `BoardRepository`, `InMemoryBoardRepository`, the relevant domain model (including the Lab 05 `CONNECTOR` invariants), the error-handling classes, and the four client-side modules and their one-directional dependency graph.

See also **[ADR-002 — Client boundaries](../ADR-002-client-boundaries.md)** for the reasoning behind the client module split.

## Quality rule

The diagrams describe the code actually delivered in this repository (see `src/main/java/edu/eci/arsw/collabboard`) — no decorative boxes, no framework classes beyond what carries architectural meaning.
