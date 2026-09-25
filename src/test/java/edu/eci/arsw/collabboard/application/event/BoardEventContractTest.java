package edu.eci.arsw.collabboard.application.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import edu.eci.arsw.collabboard.domain.model.ElementType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoardEventContractTest {

    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();

    private static final BoardEventPayload MOVE = BoardEventPayload.ofPosition("rect-1", 10.0, 20.0);

    @Test
    void envelopeRequiresEveryField() {
        Instant now = Instant.now();
        assertThrows(IllegalArgumentException.class,
                () -> new BoardEvent(" ", "board", BoardEventType.ELEMENT_MOVED, "client-a", now, MOVE));
        assertThrows(IllegalArgumentException.class,
                () -> new BoardEvent("evt", "", BoardEventType.ELEMENT_MOVED, "client-a", now, MOVE));
        assertThrows(IllegalArgumentException.class,
                () -> new BoardEvent("evt", "board", null, "client-a", now, MOVE));
        assertThrows(IllegalArgumentException.class,
                () -> new BoardEvent("evt", "board", BoardEventType.ELEMENT_MOVED, null, now, MOVE));
        assertThrows(IllegalArgumentException.class,
                () -> new BoardEvent("evt", "board", BoardEventType.ELEMENT_MOVED, "client-a", null, MOVE));
        assertThrows(IllegalArgumentException.class,
                () -> new BoardEvent("evt", "board", BoardEventType.ELEMENT_MOVED, "client-a", now, null));
    }

    @Test
    void deserializesTheDocumentedJsonEnvelope() throws Exception {
        // Same shape as the example in docs/event-contract.md.
        String json = """
                {
                  "eventId": "f2b2d7c6-1",
                  "boardId": "board-123",
                  "type": "ELEMENT_MOVED",
                  "actorId": "client-abc",
                  "occurredAt": "2026-09-01T12:30:00Z",
                  "payload": { "element": null, "elementId": "rect-1", "x": 420.0, "y": 180.0 }
                }
                """;

        BoardEvent event = mapper.readValue(json, BoardEvent.class);

        assertEquals(BoardEventType.ELEMENT_MOVED, event.type());
        assertEquals("board-123", event.boardId());
        assertEquals(Instant.parse("2026-09-01T12:30:00Z"), event.occurredAt());
        assertEquals("rect-1", event.payload().elementId());
        assertEquals(420.0, event.payload().x());
        assertNull(event.payload().element());
    }

    @Test
    void createdPayloadCarriesAFullElement() throws Exception {
        String json = """
                {
                  "eventId": "e-2", "boardId": "b", "type": "CONNECTOR_CREATED", "actorId": "client-a",
                  "occurredAt": "2026-09-01T12:30:00Z",
                  "payload": { "element": { "id": "c-1", "type": "CONNECTOR", "x": 0, "y": 0, "width": 0, "height": 0,
                                            "text": "", "sourceId": "a", "targetId": "b" } }
                }
                """;

        BoardEvent event = mapper.readValue(json, BoardEvent.class);

        assertEquals(ElementType.CONNECTOR, event.payload().element().type());
        assertEquals("a", event.payload().element().sourceId());
        assertEquals("b", event.payload().element().targetId());
    }

    @Test
    void rejectsUnknownEventTypeAndIncompleteEnvelope() {
        String unknownType = """
                {"eventId":"e","boardId":"b","type":"ELEMENT_EXPLODED","actorId":"a",
                 "occurredAt":"2026-09-01T12:30:00Z","payload":{}}
                """;
        String missingActor = """
                {"eventId":"e","boardId":"b","type":"ELEMENT_DELETED",
                 "occurredAt":"2026-09-01T12:30:00Z","payload":{"elementId":"x"}}
                """;

        assertThrows(Exception.class, () -> mapper.readValue(unknownType, BoardEvent.class));
        Exception ex = assertThrows(Exception.class, () -> mapper.readValue(missingActor, BoardEvent.class));
        assertTrue(ex.getMessage().contains("actorId is required"));
    }

    @Test
    void serializesOccurredAtAsIsoInstant() throws Exception {
        BoardEvent event = new BoardEvent("evt", "board", BoardEventType.ELEMENT_DELETED, "client-a",
                Instant.parse("2026-09-01T12:30:00Z"), BoardEventPayload.ofElementId("rect-1"));

        String json = JsonMapper.builder().findAndAddModules()
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build().writeValueAsString(event);

        assertTrue(json.contains("\"occurredAt\":\"2026-09-01T12:30:00Z\""));
        assertTrue(json.contains("\"type\":\"ELEMENT_DELETED\""));
    }
}
