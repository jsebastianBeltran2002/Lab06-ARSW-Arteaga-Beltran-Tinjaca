package edu.eci.arsw.collabboard.application.port.out;

import edu.eci.arsw.collabboard.domain.model.Board;

import java.util.Optional;

/**
 * Output port owned by the application boundary.
 * Covers exactly the operations the create/get/replace use cases need.
 */
public interface BoardRepository {
    Board save(Board board);

    Optional<Board> findById(String boardId);

    boolean existsById(String boardId);

    void deleteById(String boardId);
}
