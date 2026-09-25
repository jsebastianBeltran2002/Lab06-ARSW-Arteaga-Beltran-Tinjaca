/**
 * BoardState — the single source of truth for the client.
 * The view is only ever a projection of this state; it never keeps its
 * own copy of the board. All list operations below are pure (they return
 * new arrays instead of mutating their inputs) so they stay easy to test
 * in isolation from the DOM.
 */

export const InteractionMode = Object.freeze({
    IDLE: "idle",
    ADD_RECTANGLE: "add-rectangle",
    ADD_TEXT: "add-text",
    CONNECT: "connect",
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

export function removeElement(elements, elementId) {
    return elements
        .filter((element) => element.id !== elementId)
        .filter((element) =>
            element.type !== "CONNECTOR" ||
            (element.sourceId !== elementId && element.targetId !== elementId)
        );
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

    addRectangleAt(x, y) {
        this.mutateElements((elements) => addElement(elements, createRectangle(x, y)));
    }

    addTextAt(x, y, text) {
        this.mutateElements((elements) => addElement(elements, createText(x, y, text)));
    }

    moveElement(elementId, x, y) {
        this.mutateElements((elements) => updateElementPosition(elements, elementId, x, y));
    }

    deleteSelectedElement() {
        if (!this.state.selectedElementId) {
            return;
        }
        const elementId = this.state.selectedElementId;
        this.mutateElements((elements) => removeElement(elements, elementId));
        this.state = { ...this.state, selectedElementId: null };
        this.notify();
    }

    /** Records the first endpoint picked while in CONNECT mode. */
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
        this.mutateElements((elements) => addElement(elements, createConnector(sourceId, elementId)));
        this.state = { ...this.state, connectSourceId: null, mode: InteractionMode.IDLE, selectedElementId: null };
        this.notify();
        return { sourceId, targetId: elementId };
    }

    mutateElements(update) {
        if (!this.state.board) {
            return;
        }
        const elements = update(this.state.board.elements);
        this.state = { ...this.state, board: { ...this.state.board, elements } };
        this.notify();
    }
}
