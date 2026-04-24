package com.jobgraph.exception;

public class JobBoardApiException extends RuntimeException {
    private final String boardType;

    public JobBoardApiException(String boardType, String message) {
        super(message);
        this.boardType = boardType;
    }

    public JobBoardApiException(String boardType, String message, Throwable cause) {
        super(message, cause);
        this.boardType = boardType;
    }

    public String getBoardType() {
        return boardType;
    }
}
