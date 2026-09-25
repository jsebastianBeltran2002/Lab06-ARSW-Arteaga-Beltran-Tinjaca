# ADR-001 — Repository Boundary

## Status
Accepted

## Context
The Collaborative Board backend must let `BoardApplicationService` execute the create/get/replace use cases without knowing how or where a `Board` is stored. In this lab persistence is in-memory only, but Lab #5+ (and the course in general) implies persistence will change (thread-safety concerns, eventually a real datastore). If the application service depended directly on `InMemoryBoardRepository`, every future persistence change would force changes in the use-case layer and risk leaking storage details (e.g. `HashMap`) into business logic.

## Decision
Define `BoardRepository` as an output port (interface) in `application/port/out`, owned by the application layer. `BoardApplicationService` depends only on this interface, injected through its constructor (DIP + constructor injection, RA-02/RA-04). `InMemoryBoardRepository`, in `infrastructure/persistence`, is an adapter that implements the port using a `HashMap` and is wired by Spring via `@Repository`/`@Service` component scanning — no factory or service-locator lookup is used.

The port exposes only the three operations the use cases actually need: `save` (upsert, used by both create and replace), `findById`, and `existsById`. It intentionally does not mirror a generic CRUD/JPA-style repository (e.g. no `deleteAll`, `findAll`, pagination) since nothing in this lab's scope requires them.

## Positive consequences
- The application layer, and therefore the use cases, can be unit-tested without a web server or a real database (`BoardApplicationServiceTest` instantiates the service directly with an in-memory adapter).
- Swapping `InMemoryBoardRepository` for a different adapter (e.g. a JDBC or JPA-based one in a later lab) requires no change to `BoardApplicationService` or `BoardRestController` — only a new class implementing `BoardRepository`.
- The domain package (`domain/model`) has zero dependency on Spring or persistence types, keeping business invariants free of infrastructure concerns.

## Trade-off
An extra interface and package boundary add a small amount of indirection for what is currently a single, simple adapter. For a lab of this size the interface could be "over-engineering" if the project stopped here — but the lab explicitly requires this boundary because Lab #5+ will build on top of it, so the added indirection is justified by known near-term evolution, not speculation.

## Evidence / validation
- `BoardApplicationService` constructor takes `BoardRepository repository` (an interface), never `InMemoryBoardRepository` — see `src/main/java/edu/eci/arsw/collabboard/application/service/BoardApplicationService.java`.
- `BoardApplicationServiceTest` wires the service with `new InMemoryBoardRepository()` directly, with no Spring context, and all five tests pass (`mvn test`).
- `BoardRestController` has no reference to `HashMap`, `InMemoryBoardRepository`, or any infrastructure type — it only calls `BoardApplicationService`.
- **Sustentación:** if `InMemoryBoardRepository` were replaced by another adapter tomorrow, only a new class implementing `BoardRepository` (in `infrastructure/persistence` or a new adapter package) would need to change/be added. `BoardRepository`, `BoardApplicationService`, `BoardRestController`, `GlobalExceptionHandler`, and the domain model would remain untouched, since none of them reference the concrete adapter.
