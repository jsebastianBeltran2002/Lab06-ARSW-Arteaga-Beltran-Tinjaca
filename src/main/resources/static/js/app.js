/**
 * BoardApp — orchestrates BoardApiClient, BoardState and BoardView.
 * This is the only module that wires DOM elements, drives remote
 * operations and decides what a click means depending on the current
 * interaction mode. Neither the state nor the view know about each
 * other or about HTTP.
 */
import { ApiClientError, BoardApiClient } from "./api/board-api-client.js";
import { BoardState, InteractionMode, RemoteStatus } from "./state/board-state.js";
import { BoardView } from "./ui/board-view.js";

const apiClient = new BoardApiClient();
const state = new BoardState();

const elements = {
    boardNameInput: document.getElementById("board-name-input"),
    newBoardBtn: document.getElementById("new-board-btn"),
    loadBoardInput: document.getElementById("load-board-input"),
    loadBoardBtn: document.getElementById("load-board-btn"),
    currentBoardId: document.getElementById("current-board-id"),
    addRectangleBtn: document.getElementById("add-rectangle-btn"),
    addTextBtn: document.getElementById("add-text-btn"),
    connectBtn: document.getElementById("connect-btn"),
    deleteBtn: document.getElementById("delete-btn"),
    saveBtn: document.getElementById("save-btn"),
    statusBanner: document.getElementById("status-banner"),
    statusMessage: document.getElementById("status-message"),
    retryBtn: document.getElementById("retry-btn"),
    hint: document.getElementById("interaction-hint"),
};

const view = new BoardView(document.getElementById("board-svg"), {
    onElementInteraction: handleElementInteraction,
    onElementDragEnd: handleElementDragEnd,
    onCanvasInteraction: handleCanvasInteraction,
});

state.subscribe(render);
render(state.getState());

elements.newBoardBtn.addEventListener("click", () => {
    const name = elements.boardNameInput.value.trim();
    if (!name) {
        window.alert("Board name is required.");
        return;
    }
    state.setLastOperation({ type: "create", name });
    createBoard(name);
});

elements.loadBoardBtn.addEventListener("click", () => {
    const boardId = elements.loadBoardInput.value.trim();
    if (!boardId) {
        window.alert("Board id is required.");
        return;
    }
    state.setLastOperation({ type: "load", boardId });
    loadBoard(boardId);
});

elements.addRectangleBtn.addEventListener("click", () => state.setMode(InteractionMode.ADD_RECTANGLE));
elements.addTextBtn.addEventListener("click", () => state.setMode(InteractionMode.ADD_TEXT));
elements.connectBtn.addEventListener("click", () => state.setMode(InteractionMode.CONNECT));
elements.deleteBtn.addEventListener("click", () => state.deleteSelectedElement());

elements.saveBtn.addEventListener("click", () => {
    state.setLastOperation({ type: "save" });
    saveBoard();
});

elements.retryBtn.addEventListener("click", () => retryLastOperation());

async function runRemoteOperation(run) {
    state.setRemoteStatus(RemoteStatus.LOADING);
    try {
        const result = await run();
        state.setRemoteStatus(RemoteStatus.SUCCESS);
        return result;
    } catch (err) {
        const message = err instanceof ApiClientError
            ? `${err.code} (${err.status}): ${err.message}`
            : "Unexpected client error.";
        state.setRemoteStatus(RemoteStatus.ERROR, message);
        return null;
    }
}

async function createBoard(name) {
    const board = await runRemoteOperation(() => apiClient.createBoard(name));
    if (board) {
        state.setBoard(board);
    }
}

async function loadBoard(boardId) {
    const board = await runRemoteOperation(() => apiClient.getBoard(boardId));
    if (board) {
        state.setBoard(board);
    }
}

async function saveBoard() {
    const board = state.getState().board;
    if (!board) {
        return;
    }
    const saved = await runRemoteOperation(() => apiClient.replaceBoard(board.id, board.name, board.elements));
    if (saved) {
        state.setBoard(saved);
    }
}

function retryLastOperation() {
    const op = state.getState().lastOperation;
    if (!op) {
        return;
    }
    if (op.type === "create") {
        createBoard(op.name);
    } else if (op.type === "load") {
        loadBoard(op.boardId);
    } else if (op.type === "save") {
        saveBoard();
    }
}

function handleCanvasInteraction(x, y) {
    const current = state.getState();
    if (!current.board) {
        return;
    }
    switch (current.mode) {
        case InteractionMode.ADD_RECTANGLE:
            state.addRectangleAt(x - 60, y - 40);
            state.setMode(InteractionMode.IDLE);
            break;
        case InteractionMode.ADD_TEXT: {
            const text = window.prompt("Text content:", "");
            if (text !== null) {
                state.addTextAt(x - 70, y - 15, text);
            }
            state.setMode(InteractionMode.IDLE);
            break;
        }
        case InteractionMode.CONNECT:
            state.setMode(InteractionMode.IDLE);
            break;
        default:
            state.selectElement(null);
    }
}

function handleElementInteraction(elementId) {
    const current = state.getState();
    if (current.mode === InteractionMode.CONNECT) {
        state.pickConnectorEndpoint(elementId);
        return;
    }
    state.selectElement(elementId);
}

function handleElementDragEnd(elementId, x, y) {
    state.moveElement(elementId, x, y);
}

function render(current) {
    view.render(current);
    renderToolbar(current);
    renderStatusBanner(current);
    renderHint(current);
}

function renderToolbar(current) {
    const hasBoard = Boolean(current.board);
    const isBusy = current.remoteStatus === RemoteStatus.LOADING;

    elements.currentBoardId.textContent = hasBoard ? current.board.id : "(no board loaded)";

    elements.newBoardBtn.disabled = isBusy;
    elements.loadBoardBtn.disabled = isBusy;
    elements.addRectangleBtn.disabled = isBusy || !hasBoard;
    elements.addTextBtn.disabled = isBusy || !hasBoard;
    elements.connectBtn.disabled = isBusy || !hasBoard;
    elements.deleteBtn.disabled = isBusy || !hasBoard || !current.selectedElementId;
    elements.saveBtn.disabled = isBusy || !hasBoard;

    for (const [button, mode] of [
        [elements.addRectangleBtn, InteractionMode.ADD_RECTANGLE],
        [elements.addTextBtn, InteractionMode.ADD_TEXT],
        [elements.connectBtn, InteractionMode.CONNECT],
    ]) {
        button.classList.toggle("active", current.mode === mode);
    }
}

function renderStatusBanner(current) {
    elements.statusBanner.dataset.status = current.remoteStatus;
    elements.retryBtn.hidden = current.remoteStatus !== RemoteStatus.ERROR;

    switch (current.remoteStatus) {
        case RemoteStatus.LOADING:
            elements.statusMessage.textContent = "Working…";
            break;
        case RemoteStatus.SUCCESS:
            elements.statusMessage.textContent = "Up to date.";
            break;
        case RemoteStatus.ERROR:
            elements.statusMessage.textContent = current.errorMessage ?? "Something went wrong.";
            break;
        default:
            elements.statusMessage.textContent = "Ready.";
    }
}

function renderHint(current) {
    switch (current.mode) {
        case InteractionMode.ADD_RECTANGLE:
            elements.hint.textContent = "Click anywhere on the board to place a rectangle.";
            break;
        case InteractionMode.ADD_TEXT:
            elements.hint.textContent = "Click anywhere on the board to place a text element.";
            break;
        case InteractionMode.CONNECT:
            elements.hint.textContent = current.connectSourceId
                ? "Now click the target element to complete the connector."
                : "Click the source element for the new connector.";
            break;
        default:
            elements.hint.textContent = current.selectedElementId
                ? "Element selected — drag to move it, or use Delete."
                : "";
    }
}
