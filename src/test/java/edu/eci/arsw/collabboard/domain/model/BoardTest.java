package edu.eci.arsw.collabboard.domain.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BoardTest {

    @Test
    void shouldRejectNullOrBlankId() {
        assertThrows(IllegalArgumentException.class, () -> new Board(null, "Session", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Board(" ", "Session", List.of()));
    }

    @Test
    void shouldRejectNullOrBlankName() {
        assertThrows(IllegalArgumentException.class, () -> new Board("board-1", null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Board("board-1", " ", List.of()));
    }

    @Test
    void shouldDefaultNullElementsToEmptyList() {
        Board board = new Board("board-1", "Session", null);

        assertEquals(List.of(), board.elements());
    }

    @Test
    void shouldDefensivelyCopyElementsSoCallerMutationsDoNotLeak() {
        BoardElement element = new BoardElement("el-1", ElementType.RECTANGLE, 0, 0, 10, 10, "", null, null);
        List<BoardElement> source = new ArrayList<>(List.of(element));

        Board board = new Board("board-1", "Session", source);
        source.clear();

        assertEquals(List.of(element), board.elements());
    }

    @Test
    void shouldExposeElementsAsImmutable() {
        Board board = new Board("board-1", "Session", List.of());

        assertThrows(UnsupportedOperationException.class,
                () -> board.elements().add(new BoardElement("el-1", ElementType.TEXT, 0, 0, 1, 1, "", null, null)));
    }

    @Test
    void shouldAcceptConnectorReferencingExistingElements() {
        BoardElement source = new BoardElement("el-1", ElementType.RECTANGLE, 0, 0, 10, 10, "", null, null);
        BoardElement target = new BoardElement("el-2", ElementType.RECTANGLE, 50, 50, 10, 10, "", null, null);
        BoardElement connector = new BoardElement("c-1", ElementType.CONNECTOR, 0, 0, 0, 0, "", "el-1", "el-2");

        Board board = new Board("board-1", "Session", List.of(source, target, connector));

        assertEquals(3, board.elements().size());
    }

    @Test
    void shouldRejectConnectorReferencingNonexistentSource() {
        BoardElement target = new BoardElement("el-2", ElementType.RECTANGLE, 50, 50, 10, 10, "", null, null);
        BoardElement connector = new BoardElement("c-1", ElementType.CONNECTOR, 0, 0, 0, 0, "", "missing", "el-2");

        assertThrows(IllegalArgumentException.class,
                () -> new Board("board-1", "Session", List.of(target, connector)));
    }

    @Test
    void shouldRejectConnectorReferencingNonexistentTarget() {
        BoardElement source = new BoardElement("el-1", ElementType.RECTANGLE, 0, 0, 10, 10, "", null, null);
        BoardElement connector = new BoardElement("c-1", ElementType.CONNECTOR, 0, 0, 0, 0, "", "el-1", "missing");

        assertThrows(IllegalArgumentException.class,
                () -> new Board("board-1", "Session", List.of(source, connector)));
    }
}