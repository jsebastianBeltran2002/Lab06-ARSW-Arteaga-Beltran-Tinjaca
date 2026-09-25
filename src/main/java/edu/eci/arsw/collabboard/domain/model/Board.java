package edu.eci.arsw.collabboard.domain.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public record Board(String id, String name, List<BoardElement> elements) {
    public Board {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Board id is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Board name is required");
        }
        elements = elements == null ? List.of() : List.copyOf(elements);

        Set<String> elementIds = elements.stream().map(BoardElement::id).collect(Collectors.toSet());
        if (elementIds.size() != elements.size()) {
            throw new IllegalArgumentException("Element ids must be unique within a board");
        }
        for (BoardElement element : elements) {
            if (element.type() == ElementType.CONNECTOR) {
                if (!elementIds.contains(element.sourceId()) || !elementIds.contains(element.targetId())) {
                    throw new IllegalArgumentException(
                            "Connector " + element.id() + " must reference existing elements in the board");
                }
            }
        }
    }

    /*
     * Lab 06: state transitions used by the collaboration flow. They are pure
     * (return a new Board) and know nothing about events, STOMP or JSON; the
     * compact constructor above re-validates every invariant on the result.
     */

    public Optional<BoardElement> findElement(String elementId) {
        return elements.stream().filter(e -> e.id().equals(elementId)).findFirst();
    }

    public Board withElementAdded(BoardElement element) {
        List<BoardElement> next = new ArrayList<>(elements);
        next.add(element);
        return new Board(id, name, next);
    }

    public Board withElementMoved(String elementId, double x, double y) {
        return new Board(id, name, elements.stream()
                .map(e -> e.id().equals(elementId) ? e.movedTo(x, y) : e)
                .toList());
    }

    public Board withElementReplaced(BoardElement updated) {
        return new Board(id, name, elements.stream()
                .map(e -> e.id().equals(updated.id()) ? updated : e)
                .toList());
    }

    /** Removes the element and every connector that depends on it. */
    public Board withElementRemoved(String elementId) {
        return new Board(id, name, elements.stream()
                .filter(e -> !e.id().equals(elementId))
                .filter(e -> e.type() != ElementType.CONNECTOR
                        || (!elementId.equals(e.sourceId()) && !elementId.equals(e.targetId())))
                .toList());
    }
}
