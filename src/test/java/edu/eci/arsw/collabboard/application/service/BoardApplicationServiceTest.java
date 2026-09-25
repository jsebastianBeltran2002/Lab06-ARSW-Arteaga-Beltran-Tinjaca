package edu.eci.arsw.collabboard.application.service;

import edu.eci.arsw.collabboard.application.exception.BoardNotFoundException;
import edu.eci.arsw.collabboard.domain.model.Board;
import edu.eci.arsw.collabboard.domain.model.BoardElement;
import edu.eci.arsw.collabboard.domain.model.ElementType;
import edu.eci.arsw.collabboard.infrastructure.persistence.InMemoryBoardRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BoardApplicationServiceTest {

    private final BoardApplicationService service =
            new BoardApplicationService(new InMemoryBoardRepository());

    @Test
    void shouldCreateAndReadBoard() {
        Board created = service.createBoard("Architecture Session");
        Board loaded = service.getBoard(created.id());

        assertEquals(created, loaded);
    }

    @Test
    void shouldFailWithConcreteExceptionWhenBoardDoesNotExist() {
        assertThrows(BoardNotFoundException.class,
                () -> service.getBoard("missing-board"));
    }

    @Test
    void shouldReplaceExistingBoardKeepingItsIdentity() {
        Board created = service.createBoard("Architecture Session");
        BoardElement element = new BoardElement("el-1", ElementType.RECTANGLE, 0, 0, 10, 10, "", null, null);

        Board replaced = service.replaceBoard(created.id(), "Renamed Session", List.of(element));

        assertEquals(created.id(), replaced.id());
        assertEquals("Renamed Session", replaced.name());
        assertEquals(List.of(element), replaced.elements());
    }

    @Test
    void shouldFailToReplaceWhenBoardDoesNotExist() {
        assertThrows(BoardNotFoundException.class,
                () -> service.replaceBoard("missing-board", "Any name", List.of()));
    }

    @Test
    void shouldGenerateDistinctIdsForEachCreatedBoard() {
        Board first = service.createBoard("Session A");
        Board second = service.createBoard("Session B");

        assertNotEquals(first.id(), second.id());
    }

    @Test
    void shouldDeleteExistingBoard() {
        Board created = service.createBoard("Architecture Session");

        service.deleteBoard(created.id());

        assertThrows(BoardNotFoundException.class,
                () -> service.getBoard(created.id()));
    }

    @Test
    void shouldFailToDeleteWhenBoardDoesNotExist() {
        assertThrows(BoardNotFoundException.class,
                () -> service.deleteBoard("missing-board"));
    }
}
