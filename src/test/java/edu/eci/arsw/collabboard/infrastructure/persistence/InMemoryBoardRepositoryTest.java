package edu.eci.arsw.collabboard.infrastructure.persistence;

import edu.eci.arsw.collabboard.domain.model.Board;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryBoardRepositoryTest {

    private final InMemoryBoardRepository repository = new InMemoryBoardRepository();

    @Test
    void shouldSaveAndFindBoardById() {
        Board board = new Board("board-1", "Session", List.of());

        repository.save(board);

        assertEquals(Optional.of(board), repository.findById("board-1"));
    }

    @Test
    void shouldReturnEmptyWhenBoardIsMissing() {
        assertEquals(Optional.empty(), repository.findById("missing-board"));
    }

    @Test
    void shouldReportExistenceOnlyForKnownIds() {
        repository.save(new Board("board-1", "Session", List.of()));

        assertTrue(repository.existsById("board-1"));
        assertFalse(repository.existsById("missing-board"));
    }

    @Test
    void shouldUpsertOnSaveWithExistingId() {
        repository.save(new Board("board-1", "Original", List.of()));

        Board updated = new Board("board-1", "Renamed", List.of());
        repository.save(updated);

        assertEquals(Optional.of(updated), repository.findById("board-1"));
    }

    @Test
    void shouldDeleteExistingBoard() {
        repository.save(new Board("board-1", "Session", List.of()));

        repository.deleteById("board-1");

        assertFalse(repository.existsById("board-1"));
        assertEquals(Optional.empty(), repository.findById("board-1"));
    }

    @Test
    void shouldNotFailWhenDeletingUnknownBoard() {
        repository.deleteById("missing-board");

        assertFalse(repository.existsById("missing-board"));
    }
}
