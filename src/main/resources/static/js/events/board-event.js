/**
 * BoardEvents — builds the collaboration envelope documented in
 * docs/event-contract.md. Pure factories: no STOMP, no DOM, no state.
 * Every payload always carries the four fields (element, elementId, x, y)
 * so the backend record deserializes the same shape for every type.
 */

function newEventId() {
    return typeof crypto !== "undefined" && crypto.randomUUID
        ? crypto.randomUUID()
        : `evt-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

function envelope(type, boardId, actorId, payload) {
    return {
        eventId: newEventId(),
        boardId,
        type,
        actorId,
        occurredAt: new Date().toISOString(),
        payload: { element: null, elementId: null, x: null, y: null, ...payload },
    };
}

export const BoardEventType = Object.freeze({
    ELEMENT_CREATED: "ELEMENT_CREATED",
    ELEMENT_MOVED: "ELEMENT_MOVED",
    ELEMENT_UPDATED: "ELEMENT_UPDATED",
    ELEMENT_DELETED: "ELEMENT_DELETED",
    CONNECTOR_CREATED: "CONNECTOR_CREATED",
});

export const BoardEvents = Object.freeze({
    elementCreated(boardId, actorId, element) {
        return envelope(BoardEventType.ELEMENT_CREATED, boardId, actorId, { element });
    },
    connectorCreated(boardId, actorId, connector) {
        return envelope(BoardEventType.CONNECTOR_CREATED, boardId, actorId, { element: connector });
    },
    elementMoved(boardId, actorId, elementId, x, y) {
        return envelope(BoardEventType.ELEMENT_MOVED, boardId, actorId, { elementId, x, y });
    },
    elementUpdated(boardId, actorId, element) {
        return envelope(BoardEventType.ELEMENT_UPDATED, boardId, actorId, { element, elementId: element.id });
    },
    elementDeleted(boardId, actorId, elementId) {
        return envelope(BoardEventType.ELEMENT_DELETED, boardId, actorId, { elementId });
    },
});
