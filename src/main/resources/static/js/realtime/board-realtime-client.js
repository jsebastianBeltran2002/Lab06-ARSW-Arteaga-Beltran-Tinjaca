/**
 * BoardRealtimeClient — the only module that knows STOMP/WebSocket.
 * It owns the connection, the destinations, JSON (de)serialization and
 * publication. It never touches BoardState or the DOM: accepted events,
 * rejections and connection changes are reported through callbacks, and
 * app.js decides what they mean.
 *
 *   SEND      /app/boards/{boardId}/events
 *   SUBSCRIBE /topic/boards/{boardId}   (accepted events of this session)
 *   SUBSCRIBE /user/queue/errors        (private rejections of my events)
 *
 * Relies on the stomp.js 2.3.4 global (window.Stomp) served by the webjar.
 */

const appDestination = (boardId) => `/app/boards/${encodeURIComponent(boardId)}/events`;
const topicDestination = (boardId) => `/topic/boards/${encodeURIComponent(boardId)}`;
const ERRORS_DESTINATION = "/user/queue/errors";

function webSocketUrl() {
    const protocol = window.location.protocol === "https:" ? "wss" : "ws";
    return `${protocol}://${window.location.host}/ws`;
}

function parseBody(message) {
    try {
        return JSON.parse(message.body);
    } catch {
        console.error("Ignoring malformed STOMP message", message.body);
        return null;
    }
}

export class BoardRealtimeClient {
    /**
     * @param {{
     *   onEvent:(event:object)=>void,
     *   onRejected:(error:{code:string,message:string})=>void,
     *   onConnecting:()=>void,
     *   onConnected:(boardId:string)=>void,
     *   onDisconnected:()=>void,
     *   onConnectionError:(message:string)=>void,
     * }} handlers
     */
    constructor(handlers) {
        this.handlers = handlers;
        this.client = null;
        this.boardId = null;
    }

    isConnectedTo(boardId) {
        return Boolean(this.client?.connected) && this.boardId === boardId;
    }

    connectedBoardId() {
        return this.client?.connected ? this.boardId : null;
    }

    /** Opens the socket and subscribes to the board's session. Resolves once subscribed. */
    connect(boardId) {
        if (!boardId) {
            return Promise.reject(new Error("A board must be loaded before connecting live."));
        }
        if (!window.Stomp) {
            return Promise.reject(new Error("STOMP client library was not loaded."));
        }
        if (this.isConnectedTo(boardId)) {
            return Promise.resolve();
        }

        return this.disconnect().then(() => new Promise((resolve, reject) => {
            this.handlers.onConnecting();
            const client = window.Stomp.over(new WebSocket(webSocketUrl()));
            client.debug = null;
            this.client = client;
            let established = false;

            client.connect({}, () => {
                established = true;
                this.boardId = boardId;
                client.subscribe(topicDestination(boardId), (message) => {
                    const event = parseBody(message);
                    if (event) {
                        this.handlers.onEvent(event);
                    }
                });
                client.subscribe(ERRORS_DESTINATION, (message) => {
                    const error = parseBody(message);
                    if (error) {
                        this.handlers.onRejected(error);
                    }
                });
                this.handlers.onConnected(boardId);
                resolve();
            }, (error) => {
                // stomp.js reports both a failed handshake and a dropped socket here.
                if (this.client !== client) {
                    return; // a newer connection replaced this one
                }
                this.client = null;
                this.boardId = null;
                const detail = typeof error === "string" ? error : error?.headers?.message ?? "Connection error";
                if (established) {
                    this.handlers.onConnectionError("Live connection lost. Reconnect to keep collaborating.");
                } else {
                    this.handlers.onConnectionError(`Could not connect live: ${detail}`);
                    reject(new Error(detail));
                }
            });
        }));
    }

    publish(event) {
        if (!this.client?.connected) {
            return Promise.reject(new Error("Not connected to a live board."));
        }
        if (event.boardId !== this.boardId) {
            return Promise.reject(new Error("Event boardId does not match the connected board."));
        }
        this.client.send(appDestination(this.boardId), { "content-type": "application/json" }, JSON.stringify(event));
        return Promise.resolve();
    }

    disconnect() {
        const client = this.client;
        this.client = null;
        this.boardId = null;
        if (!client) {
            return Promise.resolve();
        }
        return new Promise((resolve) => {
            const done = () => {
                this.handlers.onDisconnected();
                resolve();
            };
            if (client.connected) {
                client.disconnect(done);
            } else {
                client.ws?.close(); // still handshaking: abort it
                done();
            }
        });
    }
}
