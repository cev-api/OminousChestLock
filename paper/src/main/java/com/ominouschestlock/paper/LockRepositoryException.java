package com.ominouschestlock.paper;

final class LockRepositoryException extends Exception {
    LockRepositoryException(String message) {
        super(message);
    }

    LockRepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
