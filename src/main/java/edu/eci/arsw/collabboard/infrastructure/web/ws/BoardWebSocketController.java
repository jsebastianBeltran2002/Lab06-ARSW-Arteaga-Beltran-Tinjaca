package edu.eci.arsw.collabboard.infrastructure.web.ws;

import edu.eci.arsw.collabboard.application.event.BoardEvent;
import edu.eci.arsw.collabboard.application.exception.BoardNotFoundException;
import edu.eci.arsw.collabboard.application.exception.InvalidBoardEventException;
import edu.eci.arsw.collabboard.application.service.BoardEventApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.time.Instant;

/**
 * STOMP adapter for live collaboration. Thin on purpose (same rule as the
 * REST controller): it checks that the destination matches the event's
 * session, delegates the transition to {@link BoardEventApplicationService}
 * and broadcasts to {@code /topic/boards/{boardId}} only if it was accepted.
 */
@Controller
public class BoardWebSocketController {

    static final String TOPIC_PREFIX = "/topic/boards/";

    private static final Logger log = LoggerFactory.getLogger(BoardWebSocketController.class);

    private final BoardEventApplicationService service;
    private final SimpMessagingTemplate messagingTemplate;

    public BoardWebSocketController(BoardEventApplicationService service,
                                    SimpMessagingTemplate messagingTemplate) {
        this.service = service;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/boards/{boardId}/events")
    public void handle(@DestinationVariable String boardId, @Payload BoardEvent event) {
        if (!boardId.equals(event.boardId())) {
            throw new InvalidBoardEventException(
                    "Destination boardId (%s) does not match event boardId (%s)".formatted(boardId, event.boardId()));
        }
        BoardEvent accepted = service.apply(event);
        messagingTemplate.convertAndSend(TOPIC_PREFIX + boardId, accepted);
    }

    @MessageExceptionHandler(BoardNotFoundException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public BoardEventError boardNotFound(BoardNotFoundException ex) {
        return reject("BOARD_NOT_FOUND", ex.getMessage());
    }

    @MessageExceptionHandler(IllegalArgumentException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public BoardEventError invalidEvent(IllegalArgumentException ex) {
        return reject("INVALID_EVENT", ex.getMessage());
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public BoardEventError unexpected(Exception ex) {
        // Malformed JSON or a broken envelope arrives wrapped by the message converter;
        // unwrap it so the sender gets the same INVALID_EVENT contract as other rejections.
        Throwable cause = ex;
        while (cause != null && !(cause instanceof IllegalArgumentException)) {
            cause = cause.getCause();
        }
        if (cause != null) {
            return reject("INVALID_EVENT", cause.getMessage());
        }
        log.warn("Unexpected error applying board event", ex);
        return reject("INTERNAL_ERROR", "Unexpected server error");
    }

    private BoardEventError reject(String code, String message) {
        log.debug("Rejected board event: {} {}", code, message);
        return new BoardEventError(Instant.now(), code, message);
    }
}
