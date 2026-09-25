/**
 * BoardApp — orchestrates BoardApiClient, BoardRealtimeClient, BoardState
 * and BoardView. This is the only module that wires DOM elements, drives
 * remote operations and decides what a click means depending on the
 * current interaction mode. Neither the state nor the view know about
 * each other, about HTTP or about STOMP.
 *
 * Lab 06: every change to the board is expressed as a BoardEvent and goes
 * through commit(). While connected live the event is only published; the
 * state changes when the server broadcasts it back as accepted, exactly as
 * it does for every other participant. Offline, the event is applied
 * locally and persisted later with Save (the Lab 05 behaviour).
 */
import { ApiClientError, BoardApiClient } from "./api/board-api-client.js";
import { BoardEvents } from "./events/board-event.js";
import { BoardRealtimeClient } from "./realtime/board-realtime-client.js";
import {
    BoardState,
    InteractionMode,
    LiveStatus,
    RemoteStatus,
    createConnector,
    createRectangle,
    createText,
} from "./state/board-state.js";
import { BoardView } from "./ui/board-view.js";

const apiClient = new BoardApiClient();
const state = new BoardState();

// One actor per page load, so two tabs of the same browser are two participants.
const actorId = `client-${(crypto.randomUUID?.() ?? Math.random().toString(36).slice(2)).slice(0, 8)}`;

const elements = {
    boardNameInput: document.getElementById("board-name-input"),
    newBoardBtn: document.getElementById("new-board-btn"),
    loadBoardInput: document.getElementById("load-board-input"),
    loadBoardBtn: document.getElementById("load-board-btn"),
    currentBoardId: document.getElementById("current-board-id"),
    addRectangleBtn: document.getElementById("add-rectangle-btn"),
    addTextBtn: document.getElementById("add-text-btn"),
    connectBtn: document.getElementById("connect-btn"),
    editTextBtn: document.getElementById("edit-text-btn"),
    deleteBtn: document.getElementById("delete-btn"),
    saveBtn: document.getElementById("save-btn"),
    connectLiveBtn: document.getElementById("connect-live-btn"),
    disconnectLiveBtn: document.getElementById("disconnect-live-btn"),
    liveStatus: document.getElementById("live-status"),
    liveMessage: document.getElementById("live-message"),
    actorId: document.getElementById("actor-id"),
    statusBanner: document.getElementById("status-banner"),
    statusMessage: document.getElementById("status-message"),
    retryBtn: document.getElementById("retry-btn"),
    hint: document.getElementById("interaction-hint"),
};

const realtime = new BoardRealtimeClient({
    // STOMP callbacks only transition BoardState; rendering follows from notify().
    onEvent: (event) => state.applyEvent(event),
    onRejected: (error) => state.setLiveStatus(state.getState().liveStatus,
        `Change rejected by the server — ${error.code}: ${error.message}`),
    onConnecting: () => state.setLiveStatus(LiveStatus.CONNECTING),
    onConnected: () => state.setLiveStatus(LiveStatus.CONNECTED),
    onDisconnected: () => state.setLiveStatus(LiveStatus.DISCONNECTED),
    onConnectionError: (message) => state.setLiveStatus(LiveStatus.ERROR, message),
});

const view = new BoardView(document.getElementById("board-svg"), {
    onElementInteraction: handleElementInteraction,
    onElementDragEnd: handleElementDragEnd,
    onCanvasInteraction: handleCanvasInteraction,
});

elements.actorId.textContent = actorId;
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
elements.editTextBtn.addEventListener("click", () => editSelectedText());
elements.deleteBtn.addEventListener("click", () => deleteSelected());

elements.saveBtn.addEventListener("click", () => {
    state.setLastOperation({ type: "save" });
    saveBoard();
});

elements.connectLiveBtn.addEventListener("click", () => connectLive());
elements.disconnectLiveBtn.addEventListener("click", () => realtime.disconnect());

elements.retryBtn.addEventListener("click", () => retryLastOperation());

// A reload keeps the board: ?board=<id> is recovered through the REST snapshot.
const boardFromUrl = new URLSearchParams(window.location.search).get("board");
if (boardFromUrl) {
    elements.loadBoardInput.value = boardFromUrl;
    state.setLastOperation({ type: "load", boardId: boardFromUrl });
    loadBoard(boardFromUrl);
}

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

/** Switching boards leaves the previous live session, so its events can never leak in. */
async function showBoard(board) {
    const liveBoardId = realtime.connectedBoardId();
    if (liveBoardId && liveBoardId !== board.id) {
        await realtime.disconnect();
    }
    state.setBoard(board);
    window.history.replaceState(null, "", `?board=${encodeURIComponent(board.id)}`);
}

async function createBoard(name) {
    const board = await runRemoteOperation(() => apiClient.createBoard(name));
    if (board) {
        await showBoard(board);
    }
}

async function loadBoard(boardId) {
    const board = await runRemoteOperation(() => apiClient.getBoard(boardId));
    if (board) {
        await showBoard(board);
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

async function connectLive() {
    const board = state.getState().board;
    if (!board) {
        return;
    }
    try {
        await realtime.connect(board.id);
    } catch {
        return; // onConnectionError already put the reason in BoardState
    }
    // Subscribed: re-read the REST snapshot so changes accepted before the
    // subscription existed are not missed. Later events apply on top of it.
    await loadBoard(board.id);
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

function isLive() {
    const board = state.getState().board;
    return Boolean(board) && realtime.isConnectedTo(board.id);
}

/**
 * Single path for every change to the board.
 * @param {(boardId:string) => object} buildEvent builds the BoardEvent for the current board
 */
function commit(buildEvent) {
    const board = state.getState().board;
    if (!board) {
        return;
    }
    const event = buildEvent(board.id);
    if (isLive()) {
        realtime.publish(event).catch((err) => state.setLiveStatus(LiveStatus.ERROR, err.message));
    } else {
        state.applyEvent(event);
    }
}

function handleCanvasInteraction(x, y) {
    const current = state.getState();
    if (!current.board) {
        return;
    }
    switch (current.mode) {
        case InteractionMode.ADD_RECTANGLE:
            commit((boardId) => BoardEvents.elementCreated(boardId, actorId, createRectangle(x - 60, y - 40)));
            state.setMode(InteractionMode.IDLE);
            break;
        case InteractionMode.ADD_TEXT: {
            const text = window.prompt("Text content:", "");
            if (text !== null) {
                commit((boardId) => BoardEvents.elementCreated(boardId, actorId, createText(x - 70, y - 15, text)));
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
        const endpoints = state.pickConnectorEndpoint(elementId);
        if (endpoints) {
            const connector = createConnector(endpoints.sourceId, endpoints.targetId);
            commit((boardId) => BoardEvents.connectorCreated(boardId, actorId, connector));
        }
        return;
    }
    state.selectElement(elementId);
}

function handleElementDragEnd(elementId, x, y) {
    // Published once, when the drag finishes — not on every pixel.
    commit((boardId) => BoardEvents.elementMoved(boardId, actorId, elementId, x, y));
}

function editSelectedText() {
    const element = state.findElement(state.getState().selectedElementId);
    if (!element || element.type === "CONNECTOR") {
        return;
    }
    const text = window.prompt("Text content:", element.text ?? "");
    if (text !== null && text !== element.text) {
        commit((boardId) => BoardEvents.elementUpdated(boardId, actorId, { ...element, text }));
    }
}

function deleteSelected() {
    const elementId = state.getState().selectedElementId;
    if (elementId) {
        commit((boardId) => BoardEvents.elementDeleted(boardId, actorId, elementId));
    }
}

function render(current) {
    view.render(current);
    renderToolbar(current);
    renderLiveBar(current);
    renderStatusBanner(current);
    renderHint(current);
}

function renderToolbar(current) {
    const hasBoard = Boolean(current.board);
    const isBusy = current.remoteStatus === RemoteStatus.LOADING;
    const selected = current.board?.elements.find((element) => element.id === current.selectedElementId);

    elements.currentBoardId.textContent = hasBoard ? current.board.id : "(no board loaded)";

    elements.newBoardBtn.disabled = isBusy;
    elements.loadBoardBtn.disabled = isBusy;
    elements.addRectangleBtn.disabled = isBusy || !hasBoard;
    elements.addTextBtn.disabled = isBusy || !hasBoard;
    elements.connectBtn.disabled = isBusy || !hasBoard;
    elements.editTextBtn.disabled = isBusy || !selected || selected.type === "CONNECTOR";
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

function renderLiveBar(current) {
    const live = current.liveStatus;
    const hasBoard = Boolean(current.board);
    const busy = live === LiveStatus.CONNECTING;
    const connected = live === LiveStatus.CONNECTED;

    elements.liveStatus.dataset.status = live;
    elements.liveStatus.textContent = connected ? `live · ${realtime.connectedBoardId()}` : live;
    elements.connectLiveBtn.disabled = !hasBoard || busy || (connected && isLive());
    elements.disconnectLiveBtn.disabled = !connected;

    const lastEvent = current.lastEvent;
    if (current.liveMessage) {
        elements.liveMessage.textContent = current.liveMessage;
    } else if (connected && lastEvent) {
        const origin = lastEvent.actorId === actorId ? "you" : lastEvent.actorId;
        elements.liveMessage.textContent = `Last accepted event: ${lastEvent.type} by ${origin}`;
    } else {
        elements.liveMessage.textContent = connected ? "" : "Offline: changes stay local until you Save.";
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
                ? "Element selected — drag to move it, Edit Text, or Delete."
                : "";
    }
}
