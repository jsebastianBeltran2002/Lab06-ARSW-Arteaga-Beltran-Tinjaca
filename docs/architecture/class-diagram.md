# Class / Module Diagram — Lab 05

Only the classes/modules that explain the main structure and their real dependency direction (RA-06). Backend classes are unchanged in shape from Lab 04 except for the `CONNECTOR`-related additions on `BoardElement`/`Board`; the client modules are new this lab.

```mermaid
classDiagram
    class BoardRestController {
        -BoardApplicationService service
        +create(CreateBoardRequest) ResponseEntity~Board~
        +get(String boardId) Board
        +replace(String boardId, ReplaceBoardRequest) Board
        +delete(String boardId) ResponseEntity~Void~
    }

    class BoardApplicationService {
        -BoardRepository repository
        +createBoard(String name) Board
        +getBoard(String boardId) Board
        +replaceBoard(String boardId, String name, List~BoardElement~) Board
        +deleteBoard(String boardId) void
    }

    class BoardRepository {
        <<interface>>
        +save(Board) Board
        +findById(String boardId) Optional~Board~
        +existsById(String boardId) boolean
    }

    class InMemoryBoardRepository {
        -Map~String, Board~ boards
        +save(Board) Board
        +findById(String boardId) Optional~Board~
        +existsById(String boardId) boolean
    }

    class Board {
        <<record>>
        +String id
        +String name
        +List~BoardElement~ elements
        Board(id, name, elements) : validates every CONNECTOR's sourceId/targetId exist among elements
    }

    class BoardElement {
        <<record>>
        +String id
        +ElementType type
        +double x
        +double y
        +double width
        +double height
        +String text
        +String sourceId
        +String targetId
        BoardElement(...) : requires distinct sourceId/targetId when type is CONNECTOR
    }

    class ElementType {
        <<enumeration>>
        RECTANGLE
        TEXT
        CONNECTOR
    }

    class BoardNotFoundException {
        -String boardId
    }

    class GlobalExceptionHandler {
        +boardNotFound(BoardNotFoundException) ResponseEntity~ApiError~
        +invalidRequest(MethodArgumentNotValidException) ResponseEntity~ApiError~
        +invalidDomainInput(IllegalArgumentException) ResponseEntity~ApiError~
        +malformedRequestBody(HttpMessageNotReadableException) ResponseEntity~ApiError~
        +starterTodo(UnsupportedOperationException) ResponseEntity~ApiError~
        +unexpected(Exception) ResponseEntity~ApiError~
    }

    class ApiError {
        <<record>>
        +Instant timestamp
        +int status
        +String code
        +String message
        +String path
    }

    BoardRestController --> BoardApplicationService : uses
    BoardApplicationService --> BoardRepository : depends on (DIP)
    InMemoryBoardRepository ..|> BoardRepository : implements
    BoardApplicationService --> Board : creates/returns
    BoardApplicationService --> BoardNotFoundException : throws
    Board *-- BoardElement : contains
    BoardElement --> ElementType : has
    GlobalExceptionHandler --> BoardNotFoundException : handles
    GlobalExceptionHandler --> ApiError : builds

    class BoardApp {
        -BoardApiClient apiClient
        -BoardState state
        -BoardView view
        +createBoard(name) void
        +loadBoard(boardId) void
        +saveBoard() void
        +retryLastOperation() void
        +handleElementInteraction/DragEnd/CanvasInteraction()
    }

    class BoardApiClient {
        +createBoard(name) Promise~Board~
        +getBoard(boardId) Promise~Board~
        +replaceBoard(boardId, name, elements) Promise~Board~
        +deleteBoard(boardId) Promise~void~
    }

    class ApiClientError {
        +status
        +code
        +message
    }

    class BoardState {
        -state : {board, selectedElementId, connectSourceId, mode, remoteStatus, errorMessage, lastOperation}
        +subscribe(listener) unsubscribe
        +setBoard(board) void
        +addRectangleAt/addTextAt(x, y) void
        +moveElement(id, x, y) void
        +deleteSelectedElement() void
        +pickConnectorEndpoint(id) result
        +setRemoteStatus(status, message) void
    }

    class BoardView {
        -svg : SVGSVGElement
        -handlers
        +render(state) void
        onMouseDown/Move/Up(evt) : classifies click vs drag
    }

    BoardApp --> BoardApiClient : calls (only module using fetch)
    BoardApp --> BoardState : reads/mutates
    BoardApp --> BoardView : render(state) + wires handlers
    BoardView --> BoardApp : reports interaction via callbacks (no direct import)
    BoardApiClient --> ApiClientError : throws on non-2xx
    BoardApp ..> BoardRestController : HTTP/JSON via BoardApiClient
```

## Key dependency directions (verifiable in code)

- `BoardRestController` → `BoardApplicationService` → `BoardRepository` (interface). No arrow points from `BoardApplicationService` to `InMemoryBoardRepository` (RA-02).
- `InMemoryBoardRepository` is the only class that implements `BoardRepository` and the only class touching the `Map` (RA-03/RA-07).
- `domain.model` classes (`Board`, `BoardElement`, `ElementType`) have no dependency on Spring, HTTP, or persistence types. `Board`'s compact constructor is also the single place that enforces the new Lab 05 invariant — every `CONNECTOR` must reference elements that exist in the same board.
- On the client: `board-api-client.js` is the only module that calls `fetch` (verifiable with `grep -rn "fetch(" src/main/resources/static/js`). `board-state.js` has zero DOM references. `board-view.js` never imports `board-api-client.js` or `board-state.js` — it only receives a plain state object to render and a set of callback handlers; `app.js` is the only module that imports all three, matching the "no cycles" requirement from the lab statement.
