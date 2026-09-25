/**
 * BoardState — the single source of truth for the client.
 * The view is only ever a projection of this state; it never keeps its
 * own copy of the board. All list operations below are pure (they return
 * new arrays instead of mutating their inputs) so they stay easy to test
 * in isolation from the DOM.
 *
 * Lab 06: every change to the board's elements is expressed as a
 * collaboration event and goes through applyEvent(), whether it was
 * accepted by the server (live) or applied locally (offline, then Save).
 * The STOMP callback therefore only transitions this state; rendering
 * happens afterwards through the normal subscribe/notify cycle.
 */

export const InteractionMode = Object.freeze({
    IDLE: "idle",
    ADD_RECTANGLE: "add-rectangle",
    ADD_TEXT: "add-text",
    CONNECT: "connect",
});

export const LiveStatus = Object.freeze({
    DISCONNECTED: "disconnected",
    CONNECTING: "connecting",
    CONNECTED: "connected",
    ERROR: "error",
});

export const RemoteStatus = Object.freeze({
    IDLE: "idle",
    LOADING: "loading",
    SUCCESS: "success",
    ERROR: "error",
});

function generateId(prefix) {
    const random = typeof crypto !== "undefined" && crypto.randomUUID
        ? crypto.randomUUID()
        : Math.random().toString(36).slice(2);
    return `${prefix}-${random}`;
}

export function addElement(elements, element) {
    return [...elements, element];
}

export function updateElementPosition(elements, elementId, x, y) {
    return elements.map((element) =>
        element.id === elementId ? { ...element, x, y } : element
    );
}

export function replaceElement(elements, updated) {
    return elements.map((element) => (element.id === updated.id ? updated : element));
}

export function removeElement(elements, elementId) {
    return elements
        .filter((element) => element.id !== elementId)
        .filter((element) =>
            element.type !== "CONNECTOR" ||
            (element.sourceId !== elementId && element.targetId !== elementId)
        );
}

/**
 * Pure transition: the element list after applying one collaboration event.
 * Idempotent for creations (an id that already exists is not duplicated),
 * so replaying an event never corrupts the list.
 */
export function applyBoardEvent(elements, event) {
    const payload = event.payload ?? {};
    switch (event.type) {
        case "ELEMENT_CREATED":
        case "CONNECTOR_CREATED":
            return elements.some((element) => element.id === payload.element.id)
                ? elements
                : addElement(elements, payload.element);
        case "ELEMENT_MOVED":
            return updateElementPosition(elements, payload.elementId, payload.x, payload.y);
        case "ELEMENT_UPDATED":
            return replaceElement(elements, payload.element);
        case "ELEMENT_DELETED":
            return removeElement(elements, payload.elementId);
        default:
            return elements;
    }
}

export function createRectangle(x, y) {
    return {
        id: generateId("rect"),
        type: "RECTANGLE",
        x,
        y,
        width: 120,
        height: 80,
        text: "",
        sourceId: null,
        targetId: null,
    };
}

export function createText(x, y, text) {
    return {
        id: generateId("text"),
        type: "TEXT",
        x,
        y,
        width: 140,
        height: 30,
        text: text ?? "",
        sourceId: null,
        targetId: null,
    };
}

export function createConnector(sourceId, targetId) {
    return {
        id: generateId("conn"),
        type: "CONNECTOR",
        x: 0,
        y: 0,
        width: 0,
        height: 0,
        text: "",
        sourceId,
        targetId,
    };
}

const initialState = () => ({
    board: null,
    selectedElementId: null,
    connectSourceId: null,
    mode: InteractionMode.IDLE,
    remoteStatus: RemoteStatus.IDLE,
    errorMessage: null,
    lastOperation: null,
    liveStatus: LiveStatus.DISCONNECTED,
    liveMessage: null,
    lastEvent: null,
});

export class BoardState {
    constructor() {
        this.state = initialState();
        this.listeners = new Set();
    }

    subscribe(listener) {
        this.listeners.add(listener);
        return () => this.listeners.delete(listener);
    }

    notify() {
        for (const listener of this.listeners) {
            listener(this.state);
        }
    }

    getState() {
        return this.state;
    }

    setBoard(board) {
        this.state = {
            ...this.state,
            board,
            selectedElementId: null,
            connectSourceId: null,
            mode: InteractionMode.IDLE,
        };
        this.notify();
    }

    setRemoteStatus(remoteStatus, errorMessage = null) {
        this.state = { ...this.state, remoteStatus, errorMessage };
        this.notify();
    }

    setLastOperation(lastOperation) {
        this.state = { ...this.state, lastOperation };
    }

    setMode(mode) {
        this.state = { ...this.state, mode, connectSourceId: null };
        this.notify();
    }

    selectElement(elementId) {
        this.state = { ...this.state, selectedElementId: elementId };
        this.notify();
    }

    setLiveStatus(liveStatus, liveMessage = null) {
        this.state = { ...this.state, liveStatus, liveMessage };
        this.notify();
    }

    findElement(elementId) {
        return this.state.board?.elements.find((element) => element.id === elementId) ?? null;
    }

    /**
     * Applies an accepted (or offline) event to the current board and
     * notifies listeners. Events for another board are ignored, which keeps
     * sessions isolated even if a stale message arrives after switching.
     * Returns true when the event was applied.
     */
    applyEvent(event) {
        const board = this.state.board;
        if (!board || event.boardId !== board.id) {
            return false;
        }
        const elements = applyBoardEvent(board.elements, event);
        const exists = (id) => id !== null && elements.some((element) => element.id === id);
        this.state = {
            ...this.state,
            board: { ...board, elements },
            selectedElementId: exists(this.state.selectedElementId) ? this.state.selectedElementId : null,
            connectSourceId: exists(this.state.connectSourceId) ? this.state.connectSourceId : null,
            lastEvent: { type: event.type, actorId: event.actorId },
            liveMessage: null, // a newer accepted change supersedes the last rejection notice
        };
        this.notify();
        return true;
    }

    /**
     * Records the endpoints picked while in CONNECT mode. Returns
     * {sourceId, targetId} once both are chosen (and leaves CONNECT mode);
     * the caller turns that into a CONNECTOR_CREATED event.
     */
    pickConnectorEndpoint(elementId) {
        if (!this.state.connectSourceId) {
            this.state = { ...this.state, connectSourceId: elementId, selectedElementId: elementId };
            this.notify();
            return null;
        }
        if (this.state.connectSourceId === elementId) {
            return null;
        }
        const sourceId = this.state.connectSourceId;
        this.state = { ...this.state, connectSourceId: null, mode: InteractionMode.IDLE, selectedElementId: null };
        this.notify();
        return { sourceId, targetId: elementId };
    }
}
