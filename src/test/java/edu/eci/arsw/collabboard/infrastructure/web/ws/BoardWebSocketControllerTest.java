package edu.eci.arsw.collabboard.infrastructure.web.ws;

import edu.eci.arsw.collabboard.application.event.BoardEvent;
import edu.eci.arsw.collabboard.application.event.BoardEventPayload;
import edu.eci.arsw.collabboard.application.event.BoardEventType;
import edu.eci.arsw.collabboard.application.exception.BoardNotFoundException;
import edu.eci.arsw.collabboard.application.exception.InvalidBoardEventException;
import edu.eci.arsw.collabboard.application.service.BoardEventApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BoardWebSocketControllerTest {

    private final BoardEventApplicationService service = mock(BoardEventApplicationService.class);
    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    private final BoardWebSocketController controller = new BoardWebSocketController(service, messagingTemplate);

    private static BoardEvent deleteEvent(String boardId) {
        return new BoardEvent("evt-1", boardId, BoardEventType.ELEMENT_DELETED, "client-a",
                Instant.now(), BoardEventPayload.ofElementId("rect-1"));
    }

    @Test
    void acceptedEventIsBroadcastToItsBoardTopic() {
        BoardEvent event = deleteEvent("board-1");
        when(service.apply(event)).thenReturn(event);

        controller.handle("board-1", event);

        verify(service).apply(event);
        verify(messagingTemplate).convertAndSend("/topic/boards/board-1", event);
    }

    @Test
    void rejectedEventIsNeverBroadcast() {
        BoardEvent event = deleteEvent("board-1");
        when(service.apply(event)).thenThrow(new InvalidBoardEventException("Element not found in board: rect-1"));

        assertThrows(InvalidBoardEventException.class, () -> controller.handle("board-1", event));

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void destinationAndEventBoardMustMatch() {
        BoardEvent event = deleteEvent("board-2");

        assertThrows(InvalidBoardEventException.class, () -> controller.handle("board-1", event));

        verifyNoInteractions(service, messagingTemplate);
    }

    @Test
    void rejectionsAreTranslatedToAnErrorContract() {
        assertEquals("BOARD_NOT_FOUND", controller.boardNotFound(new BoardNotFoundException("b")).code());
        assertEquals("INVALID_EVENT", controller.invalidEvent(new InvalidBoardEventException("bad")).code());

        BoardEventError wrapped = controller.unexpected(
                new MessageConversionException("cannot read", new IllegalArgumentException("actorId is required")));
        assertEquals("INVALID_EVENT", wrapped.code());
        assertEquals("actorId is required", wrapped.message());

        BoardEventError internal = controller.unexpected(new IllegalStateException("boom"));
        assertEquals("INTERNAL_ERROR", internal.code());
        assertEquals("Unexpected server error", internal.message());
    }
}
