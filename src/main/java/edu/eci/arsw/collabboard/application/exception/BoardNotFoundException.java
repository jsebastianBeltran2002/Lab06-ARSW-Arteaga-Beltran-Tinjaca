package edu.eci.arsw.collabboard.application.exception;

public class BoardNotFoundException extends RuntimeException {
    private final String boardId;

    public BoardNotFoundException(String boardId) {
        super("Board not found: " + boardId);
        this.boardId = boardId;
    }

    public String boardId() {
        return boardId;
    }
}
