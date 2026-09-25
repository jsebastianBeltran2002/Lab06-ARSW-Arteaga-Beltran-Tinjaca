package edu.eci.arsw.collabboard.application.event;

/**
 * Semantics of a collaboration message. Each type defines which
 * {@link BoardEventPayload} fields are required (see docs/event-contract.md).
 */
public enum BoardEventType {
    ELEMENT_CREATED,
    ELEMENT_MOVED,
    ELEMENT_UPDATED,
    ELEMENT_DELETED,
    CONNECTOR_CREATED
}
