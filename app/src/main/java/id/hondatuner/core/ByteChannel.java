package id.hondatuner.core;

import java.io.IOException;

/** UART bytes after USB status removal. Implementations must bound every I/O. */
public interface ByteChannel extends AutoCloseable {
    void initialize(CancelSignal cancel) throws IOException;
    /** Discard pending RX only; never cancel a queued UART transmission. */
    void purge() throws IOException;
    /** Return after all bytes have left the UART, within the supplied timeout. */
    void write(byte[] data, int timeoutMillis, CancelSignal cancel) throws IOException;
    byte[] read(int maximumBytes, int timeoutMillis) throws IOException;
    /** Cached state only; this must never wait for USB or a wire-operation lock. */
    default boolean isHealthy() { return true; }
    /** Revoke the channel immediately; implementations may finish USB cleanup asynchronously. */
    @Override void close();
}
