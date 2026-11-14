package id.hondatuner.core;

import java.io.InterruptedIOException;

public interface CancelSignal {
    CancelSignal NEVER = () -> false;
    boolean isCancelled();

    default void check() throws InterruptedIOException {
        if (isCancelled() || Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("Pembacaan dibatalkan.");
        }
    }
}
