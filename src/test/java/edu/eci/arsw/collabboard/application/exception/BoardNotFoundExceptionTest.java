package edu.eci.arsw.collabboard.application.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class BoardNotFoundExceptionTest {

    @Test
    void shouldExposeBoardIdAndDescriptiveMessage() {
        BoardNotFoundException exception = new BoardNotFoundException("missing-board");

        assertEquals("missing-board", exception.boardId());
        assertEquals("Board not found: missing-board", exception.getMessage());
    }

    @Test
    void shouldBeAnUncheckedException() {
        assertInstanceOf(RuntimeException.class, new BoardNotFoundException("missing-board"));
    }
}
