package edu.eci.arsw.collabboard.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BoardElementTest {

    @Test
    void shouldRejectNullOrBlankId() {
        assertThrows(IllegalArgumentException.class,
                () -> new BoardElement(null, ElementType.RECTANGLE, 0, 0, 1, 1, "", null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new BoardElement(" ", ElementType.RECTANGLE, 0, 0, 1, 1, "", null, null));
    }

    @Test
    void shouldRejectNullType() {
        assertThrows(IllegalArgumentException.class,
                () -> new BoardElement("el-1", null, 0, 0, 1, 1, "", null, null));
    }

    @Test
    void shouldRejectNegativeWidthOrHeight() {
        assertThrows(IllegalArgumentException.class,
                () -> new BoardElement("el-1", ElementType.RECTANGLE, 0, 0, -1, 1, "", null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new BoardElement("el-1", ElementType.RECTANGLE, 0, 0, 1, -1, "", null, null));
    }

    @Test
    void shouldDefaultNullTextToEmptyString() {
        BoardElement element = new BoardElement("el-1", ElementType.TEXT, 0, 0, 1, 1, null, null, null);

        assertEquals("", element.text());
    }

    @Test
    void shouldAcceptZeroWidthAndHeight() {
        BoardElement element = new BoardElement("el-1", ElementType.RECTANGLE, 0, 0, 0, 0, "", null, null);

        assertEquals(0, element.width());
        assertEquals(0, element.height());
    }

    @Test
    void shouldRequireSourceAndTargetForConnector() {
        assertThrows(IllegalArgumentException.class,
                () -> new BoardElement("c-1", ElementType.CONNECTOR, 0, 0, 0, 0, "", null, "b"));
        assertThrows(IllegalArgumentException.class,
                () -> new BoardElement("c-1", ElementType.CONNECTOR, 0, 0, 0, 0, "", "a", null));
    }

    @Test
    void shouldRejectConnectorWithSameSourceAndTarget() {
        assertThrows(IllegalArgumentException.class,
                () -> new BoardElement("c-1", ElementType.CONNECTOR, 0, 0, 0, 0, "", "a", "a"));
    }
}