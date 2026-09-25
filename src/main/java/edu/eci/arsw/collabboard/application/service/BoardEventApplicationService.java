package edu.eci.arsw.collabboard.application.service;

import edu.eci.arsw.collabboard.application.event.BoardEvent;
import edu.eci.arsw.collabboard.application.event.BoardEventPayload;
import edu.eci.arsw.collabboard.application.exception.BoardNotFoundException;
import edu.eci.arsw.collabboard.application.exception.InvalidBoardEventException;
import edu.eci.arsw.collabboard.application.port.out.BoardRepository;
import edu.eci.arsw.collabboard.domain.model.Board;
import edu.eci.arsw.collabboard.domain.model.BoardElement;
import edu.eci.arsw.collabboard.domain.model.ElementType;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * Applies collaboration events to the authoritative Board state.
 *
 * <p>Maps each {@link BoardEvent} to a pure transition on {@link Board},
 * validates the payload required by its type and persists the result through
 * the {@link BoardRepository} port. Only an event returned from
 * {@link #apply(BoardEvent)} may be broadcast to the session.
 *
 * <p>Concurrency is intentionally NOT addressed here: the find → transition →
 * save sequence below is a non-atomic read-modify-write. Lab #7 will stress
 * this exact path with simultaneous clients.
 */
@Service
public class BoardEventApplicationService {

    private final BoardRepository repository;

    public BoardEventApplicationService(BoardRepository repository) {
        this.repository = repository;
    }

    public BoardEvent apply(BoardEvent event) {
        Board board = repository.findById(event.boardId())
                .orElseThrow(() -> new BoardNotFoundException(event.boardId()));

        Board next = switch (event.type()) {
            case ELEMENT_CREATED -> created(board, event.payload());
            case CONNECTOR_CREATED -> connectorCreated(board, event.payload());
            case ELEMENT_MOVED -> moved(board, event.payload());
            case ELEMENT_UPDATED -> updated(board, event.payload());
            case ELEMENT_DELETED -> deleted(board, event.payload());
        };

        repository.save(next);
        return event;
    }

    private Board created(Board board, BoardEventPayload payload) {
        BoardElement element = requireElement(payload);
        if (element.type() == ElementType.CONNECTOR) {
            throw new InvalidBoardEventException("ELEMENT_CREATED only accepts RECTANGLE or TEXT; use CONNECTOR_CREATED");
        }
        requireNewId(board, element);
        return transition(() -> board.withElementAdded(element));
    }

    private Board connectorCreated(Board board, BoardEventPayload payload) {
        BoardElement connector = requireElement(payload);
        if (connector.type() != ElementType.CONNECTOR) {
            throw new InvalidBoardEventException("CONNECTOR_CREATED requires an element of type CONNECTOR");
        }
        requireNewId(board, connector);
        requireExisting(board, connector.sourceId());
        requireExisting(board, connector.targetId());
        return transition(() -> board.withElementAdded(connector));
    }

    private Board moved(Board board, BoardEventPayload payload) {
        if (payload.x() == null || payload.y() == null) {
            throw new InvalidBoardEventException("ELEMENT_MOVED requires x and y");
        }
        BoardElement current = requireExisting(board, requireElementId(payload));
        if (current.type() == ElementType.CONNECTOR) {
            throw new InvalidBoardEventException("Connectors are positioned by their endpoints and cannot be moved");
        }
        return transition(() -> board.withElementMoved(current.id(), payload.x(), payload.y()));
    }

    private Board updated(Board board, BoardEventPayload payload) {
        BoardElement updated = requireElement(payload);
        BoardElement current = requireExisting(board, updated.id());
        if (current.type() != updated.type()) {
            throw new InvalidBoardEventException("ELEMENT_UPDATED cannot change the type of element " + updated.id());
        }
        return transition(() -> board.withElementReplaced(updated));
    }

    private Board deleted(Board board, BoardEventPayload payload) {
        String elementId = requireElementId(payload);
        requireExisting(board, elementId);
        return board.withElementRemoved(elementId);
    }

    private static BoardElement requireElement(BoardEventPayload payload) {
        if (payload.element() == null) {
            throw new InvalidBoardEventException("payload.element is required for this event type");
        }
        return payload.element();
    }

    private static String requireElementId(BoardEventPayload payload) {
        if (payload.elementId() == null || payload.elementId().isBlank()) {
            throw new InvalidBoardEventException("payload.elementId is required for this event type");
        }
        return payload.elementId();
    }

    private static BoardElement requireExisting(Board board, String elementId) {
        return board.findElement(elementId)
                .orElseThrow(() -> new InvalidBoardEventException("Element not found in board: " + elementId));
    }

    private static void requireNewId(Board board, BoardElement element) {
        if (board.findElement(element.id()).isPresent()) {
            throw new InvalidBoardEventException("Element id already exists in board: " + element.id());
        }
    }

    /** Surfaces a broken Board invariant as a rejected event instead of a generic error. */
    private static Board transition(Supplier<Board> transition) {
        try {
            return transition.get();
        } catch (InvalidBoardEventException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            throw new InvalidBoardEventException(ex.getMessage());
        }
    }
}
