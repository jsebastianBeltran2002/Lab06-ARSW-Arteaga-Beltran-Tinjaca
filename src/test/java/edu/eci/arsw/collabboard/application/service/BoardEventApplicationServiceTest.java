package edu.eci.arsw.collabboard.application.service;

import edu.eci.arsw.collabboard.application.event.BoardEvent;
import edu.eci.arsw.collabboard.application.event.BoardEventPayload;
import edu.eci.arsw.collabboard.application.event.BoardEventType;
import edu.eci.arsw.collabboard.application.exception.BoardNotFoundException;
import edu.eci.arsw.collabboard.application.exception.InvalidBoardEventException;
import edu.eci.arsw.collabboard.domain.model.Board;
import edu.eci.arsw.collabboard.domain.model.BoardElement;
import edu.eci.arsw.collabboard.domain.model.ElementType;
import edu.eci.arsw.collabboard.infrastructure.persistence.InMemoryBoardRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BoardEventApplicationServiceTest {

    private static final String BOARD_ID = "board-1";

    private final InMemoryBoardRepository repository = new InMemoryBoardRepository();
    private final BoardEventApplicationService service = new BoardEventApplicationService(repository);

    private static BoardElement rectangle(String id) {
        return new BoardElement(id, ElementType.RECTANGLE, 10, 10, 120, 80, "", null, null);
    }

    private static BoardElement text(String id) {
        return new BoardElement(id, ElementType.TEXT, 200, 40, 140, 30, "note", null, null);
    }

    private static BoardElement connector(String id, String sourceId, String targetId) {
        return new BoardElement(id, ElementType.CONNECTOR, 0, 0, 0, 0, "", sourceId, targetId);
    }

    private static BoardEvent event(BoardEventType type, BoardEventPayload payload) {
        return new BoardEvent("evt-" + type, BOARD_ID, type, "client-a", Instant.now(), payload);
    }

    private Board givenBoard(BoardElement... elements) {
        return repository.save(new Board(BOARD_ID, "Architecture Board", List.of(elements)));
    }

    private Board storedBoard() {
        return repository.findById(BOARD_ID).orElseThrow();
    }

    @Test
    void elementCreatedAddsRectangleAndReturnsTheAcceptedEvent() {
        givenBoard();
        BoardEvent event = event(BoardEventType.ELEMENT_CREATED, BoardEventPayload.ofElement(rectangle("rect-1")));

        BoardEvent accepted = service.apply(event);

        assertSame(event, accepted);
        assertEquals(List.of(rectangle("rect-1")), storedBoard().elements());
    }

    @Test
    void elementCreatedAddsText() {
        givenBoard();

        service.apply(event(BoardEventType.ELEMENT_CREATED, BoardEventPayload.ofElement(text("text-1"))));

        assertEquals(ElementType.TEXT, storedBoard().elements().get(0).type());
    }

    @Test
    void elementCreatedRejectsConnectorsSoTheyUseTheirOwnEventType() {
        givenBoard(rectangle("a"), rectangle("b"));

        assertThrows(InvalidBoardEventException.class, () -> service.apply(
                event(BoardEventType.ELEMENT_CREATED, BoardEventPayload.ofElement(connector("c-1", "a", "b")))));
        assertEquals(2, storedBoard().elements().size());
    }

    @Test
    void elementCreatedRejectsDuplicateId() {
        givenBoard(rectangle("rect-1"));

        assertThrows(InvalidBoardEventException.class, () -> service.apply(
                event(BoardEventType.ELEMENT_CREATED, BoardEventPayload.ofElement(text("rect-1")))));
    }

    @Test
    void elementMovedUpdatesOnlyThePosition() {
        givenBoard(rectangle("rect-1"));

        service.apply(event(BoardEventType.ELEMENT_MOVED, BoardEventPayload.ofPosition("rect-1", 42.0, 84.0)));

        BoardElement moved = storedBoard().elements().get(0);
        assertEquals(42.0, moved.x());
        assertEquals(84.0, moved.y());
        assertEquals(120.0, moved.width());
        assertEquals(ElementType.RECTANGLE, moved.type());
    }

    @Test
    void elementMovedRejectsUnknownElementAndMissingCoordinates() {
        givenBoard(rectangle("rect-1"));

        assertThrows(InvalidBoardEventException.class, () -> service.apply(
                event(BoardEventType.ELEMENT_MOVED, BoardEventPayload.ofPosition("ghost", 1, 1))));
        assertThrows(InvalidBoardEventException.class, () -> service.apply(
                event(BoardEventType.ELEMENT_MOVED, new BoardEventPayload(null, "rect-1", null, 5.0))));
    }

    @Test
    void elementMovedRejectsConnectors() {
        givenBoard(rectangle("a"), rectangle("b"), connector("c-1", "a", "b"));

        assertThrows(InvalidBoardEventException.class, () -> service.apply(
                event(BoardEventType.ELEMENT_MOVED, BoardEventPayload.ofPosition("c-1", 1, 1))));
    }

    @Test
    void connectorCreatedLinksTheSameEndpoints() {
        givenBoard(rectangle("a"), text("b"));

        service.apply(event(BoardEventType.CONNECTOR_CREATED, BoardEventPayload.ofElement(connector("c-1", "a", "b"))));

        BoardElement stored = storedBoard().findElement("c-1").orElseThrow();
        assertEquals("a", stored.sourceId());
        assertEquals("b", stored.targetId());
    }

    @Test
    void connectorCreatedRejectsMissingEndpointAndNonConnectorElements() {
        givenBoard(rectangle("a"));

        assertThrows(InvalidBoardEventException.class, () -> service.apply(
                event(BoardEventType.CONNECTOR_CREATED, BoardEventPayload.ofElement(connector("c-1", "a", "ghost")))));
        assertThrows(InvalidBoardEventException.class, () -> service.apply(
                event(BoardEventType.CONNECTOR_CREATED, BoardEventPayload.ofElement(rectangle("rect-2")))));
        assertEquals(1, storedBoard().elements().size());
    }

    @Test
    void elementUpdatedReplacesEditableProperties() {
        givenBoard(text("text-1"));
        BoardElement edited = new BoardElement("text-1", ElementType.TEXT, 200, 40, 140, 30, "edited", null, null);

        service.apply(event(BoardEventType.ELEMENT_UPDATED, BoardEventPayload.ofElement(edited)));

        assertEquals("edited", storedBoard().elements().get(0).text());
    }

    @Test
    void elementUpdatedCannotChangeTheElementType() {
        givenBoard(text("text-1"));
        BoardElement retyped = new BoardElement("text-1", ElementType.RECTANGLE, 0, 0, 10, 10, "", null, null);

        assertThrows(InvalidBoardEventException.class, () -> service.apply(
                event(BoardEventType.ELEMENT_UPDATED, BoardEventPayload.ofElement(retyped))));
    }

    @Test
    void elementDeletedAlsoRemovesDependentConnectors() {
        givenBoard(rectangle("a"), rectangle("b"), rectangle("c"),
                connector("a-b", "a", "b"), connector("b-c", "b", "c"), connector("a-c", "a", "c"));

        service.apply(event(BoardEventType.ELEMENT_DELETED, BoardEventPayload.ofElementId("b")));

        List<String> remaining = storedBoard().elements().stream().map(BoardElement::id).toList();
        assertEquals(List.of("a", "c", "a-c"), remaining);
    }

    @Test
    void elementDeletedRejectsUnknownElement() {
        givenBoard(rectangle("a"));

        assertThrows(InvalidBoardEventException.class, () -> service.apply(
                event(BoardEventType.ELEMENT_DELETED, BoardEventPayload.ofElementId("ghost"))));
    }

    @Test
    void eventForUnknownBoardIsRejected() {
        assertThrows(BoardNotFoundException.class, () -> service.apply(
                event(BoardEventType.ELEMENT_CREATED, BoardEventPayload.ofElement(rectangle("rect-1")))));
    }

    @Test
    void eventsOnlyChangeTheirOwnBoard() {
        givenBoard();
        repository.save(new Board("board-2", "Other Board", List.of()));

        service.apply(event(BoardEventType.ELEMENT_CREATED, BoardEventPayload.ofElement(rectangle("rect-1"))));

        assertEquals(1, storedBoard().elements().size());
        assertEquals(0, repository.findById("board-2").orElseThrow().elements().size());
    }
}
