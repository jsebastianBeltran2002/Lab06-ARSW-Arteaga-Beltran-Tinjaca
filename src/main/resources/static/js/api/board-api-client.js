/**
 * BoardApiClient — the only module allowed to call fetch().
 * Every non-2xx response is translated into an ApiClientError with a
 * useful status/code/message so the rest of the client never has to
 * understand HTTP or the backend's ApiError shape.
 */

const BASE_URL = "/api/boards";

export class ApiClientError extends Error {
    constructor(message, status, code) {
        super(message);
        this.name = "ApiClientError";
        this.status = status;
        this.code = code;
    }
}

async function toApiClientError(response) {
    let code = "UNKNOWN_ERROR";
    let message = `Request failed with status ${response.status}`;
    try {
        const body = await response.json();
        code = body.code ?? code;
        message = body.message ?? message;
    } catch {
        // Response had no JSON body (e.g. network-level failure surfaced as a response) — keep the defaults.
    }
    return new ApiClientError(message, response.status, code);
}

async function request(path, options) {
    let response;
    try {
        response = await fetch(path, options);
    } catch {
        throw new ApiClientError("Could not reach the server. Check your connection and retry.", 0, "NETWORK_ERROR");
    }

    if (!response.ok) {
        throw await toApiClientError(response);
    }

    if (response.status === 204) {
        return null;
    }
    return response.json();
}

export class BoardApiClient {
    createBoard(name) {
        return request(BASE_URL, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ name }),
        });
    }

    getBoard(boardId) {
        return request(`${BASE_URL}/${encodeURIComponent(boardId)}`, {
            method: "GET",
        });
    }

    replaceBoard(boardId, name, elements) {
        return request(`${BASE_URL}/${encodeURIComponent(boardId)}`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ name, elements }),
        });
    }

    deleteBoard(boardId) {
        return request(`${BASE_URL}/${encodeURIComponent(boardId)}`, {
            method: "DELETE",
        });
    }
}
