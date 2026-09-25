package edu.eci.arsw.collabboard.application.event;

import edu.eci.arsw.collabboard.domain.model.BoardElement;

/**
 * Minimal data carried by a {@link BoardEvent}. Which fields are required
 * depends on the event type:
 * <ul>
 *     <li>ELEMENT_CREATED / CONNECTOR_CREATED / ELEMENT_UPDATED: {@code element}</li>
 *     <li>ELEMENT_MOVED: {@code elementId}, {@code x}, {@code y}</li>
 *     <li>ELEMENT_DELETED: {@code elementId}</li>
 * </ul>
 * Per-type validation lives in the application service, not here, so the
 * contract stays a plain message shape.
 */
public record BoardEventPayload(
        BoardElement element,
        String elementId,
        Double x,
        Double y
) {
    public static BoardEventPayload ofElement(BoardElement element) {
        return new BoardEventPayload(element, null, null, null);
    }

    public static BoardEventPayload ofPosition(String elementId, double x, double y) {
        return new BoardEventPayload(null, elementId, x, y);
    }

    public static BoardEventPayload ofElementId(String elementId) {
        return new BoardEventPayload(null, elementId, null, null);
    }
}
