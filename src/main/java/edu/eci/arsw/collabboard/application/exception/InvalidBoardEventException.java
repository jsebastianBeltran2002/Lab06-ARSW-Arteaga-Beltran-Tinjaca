package edu.eci.arsw.collabboard.application.exception;

/**
 * Raised when a collaboration event is well-formed as an envelope but cannot
 * be applied to the current Board state (wrong payload for its type, unknown
 * element, a transition that would break a Board invariant, ...).
 */
public class InvalidBoardEventException extends IllegalArgumentException {
    public InvalidBoardEventException(String message) {
        super(message);
    }
}
