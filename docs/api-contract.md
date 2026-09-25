# REST Contract — Lab 04 (evolved in Lab 05)

Base path: `/api/boards`

| Method | Resource | Request | Success response | Error cases |
|---|---|---|---|---|
| POST | `/api/boards` | `{ "name": "string (required, not blank)" }` | `201 Created` + created `Board` (server-generated `id`, empty `elements`) | `400 INVALID_REQUEST` if `name` is blank/missing |
| GET | `/api/boards/{boardId}` | - | `200 OK` + `Board` | `404 BOARD_NOT_FOUND` if `boardId` does not exist |
| PUT | `/api/boards/{boardId}` | `{ "name": "string (required)", "elements": [BoardElement...] (required) }` | `200 OK` + updated `Board` (same `id`, new `name`/`elements`) | `404 BOARD_NOT_FOUND` if `boardId` does not exist; `400 INVALID_REQUEST`/`400 INVALID_INPUT` if the payload or an element is invalid |
| DELETE | `/api/boards/{boardId}` | - | `204 No Content` | `404 BOARD_NOT_FOUND` if `boardId` does not exist |

## Board representation

```json
{
  "id": "5e6f2c2a-...-uuid",
  "name": "Architecture Session",
  "elements": [
    {
      "id": "el-1",
      "type": "RECTANGLE",
      "x": 10.0,
      "y": 20.0,
      "width": 120.0,
      "height": 80.0,
      "text": ""
    },
    {
      "id": "el-2",
      "type": "TEXT",
      "x": 200.0,
      "y": 40.0,
      "width": 140.0,
      "height": 30.0,
      "text": "note"
    },
    {
      "id": "c-1",
      "type": "CONNECTOR",
      "x": 0.0,
      "y": 0.0,
      "width": 0.0,
      "height": 0.0,
      "text": "",
      "sourceId": "el-1",
      "targetId": "el-2"
    }
  ]
}
```

`type` is one of: `RECTANGLE`, `TEXT`, `CONNECTOR`.

## Element shape per type (Lab 05)

| Type | x/y/width/height | text | sourceId/targetId |
|---|---|---|---|
| `RECTANGLE` | Used for rendering | Optional (defaults to `""`) | Not used |
| `TEXT` | Used for rendering | Content shown to the user | Not used |
| `CONNECTOR` | Not used for rendering — the client draws a line between the centers of `sourceId`/`targetId` | Not used | Required, must be different from each other, and must both reference element `id`s present in the same `elements` list at save time |

`sourceId`/`targetId` are validated in two layers, consistently with the rest of the domain's fail-fast style:

1. `BoardElement` (per-element invariant): for a `CONNECTOR`, both fields are required and must differ from each other.
2. `Board` (aggregate invariant, added in Lab 05): every `CONNECTOR`'s `sourceId`/`targetId` must match the `id` of another element present in the same `elements` list. This is checked whenever a `Board` is constructed — i.e. on every `POST`/`PUT` — so a connector can never reference an element that doesn't exist (or that was removed in the same save).

Both violations surface as `400 INVALID_INPUT`, same as any other domain invariant.

## Error contract

Every error response (thrown from the application or infrastructure layer) is translated by `GlobalExceptionHandler` into the same shape:

```json
{
  "timestamp": "2026-09-04T12:00:00Z",
  "status": 404,
  "code": "BOARD_NOT_FOUND",
  "message": "Board not found: missing-board",
  "path": "/api/boards/missing-board"
}
```

| HTTP status | `code` | When |
|---|---|---|
| 404 | `BOARD_NOT_FOUND` | `GET`/`PUT`/`DELETE` on a `boardId` that does not exist |
| 400 | `INVALID_REQUEST` | Bean validation failure on the request body (e.g. blank `name`) |
| 400 | `INVALID_INPUT` | Domain invariant violated (e.g. invalid `BoardElement`) |
| 500 | `INTERNAL_ERROR` | Any unexpected exception — no internal message or stack trace is ever returned |

## Deviations from the starter template

The starter template covers create/get/replace only. `DELETE /api/boards/{boardId}` was added on top of it to
complete basic CRUD on the boards resource, reusing the same error shape and the existing
`BoardNotFoundException` handling.

## Lab 05 — no new endpoints

The interactive web client (see `docs/ADR-002-client-boundaries.md`) consumes exactly the four endpoints above.
Per the lab's design constraints, no `/moveElement`, `/drawRectangle` or similar action-specific endpoints were
added: move/add/delete/connect are local `BoardState` mutations on the client, and `PUT /api/boards/{boardId}`
(full replace) is the single persistence operation, triggered only by an explicit **Save** action.
