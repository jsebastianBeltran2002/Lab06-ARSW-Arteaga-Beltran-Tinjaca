package edu.eci.arsw.collabboard.application.event;

import java.time.Instant;

/**
 * Collaboration envelope exchanged between participants of a Board session.
 *
 * <p>This is a communication contract, not a domain entity: {@code Board} and
 * {@code BoardElement} keep representing the state of the problem, while a
 * {@code BoardEvent} only describes a requested/accepted transition over it.
 */
public record BoardEvent(
        String eventId,
        String boardId,
        BoardEventType type,
        String actorId,
        Instant occurredAt,
        BoardEventPayload payload
) {
    public BoardEvent {
        requireText(eventId, "eventId");
        requireText(boardId, "boardId");
        requireText(actorId, "actorId");
        if (type == null) {
            throw new IllegalArgumentException("type is required");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt is required");
        }
        if (payload == null) {
            throw new IllegalArgumentException("payload is required");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
