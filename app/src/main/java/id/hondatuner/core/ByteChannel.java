package id.hondatuner.core;

import java.io.IOException;

/** UART bytes after USB status removal. Implementations must bound every I/O. */
public interface ByteChannel extends AutoCloseable {
    void initialize(CancelSignal cancel) throws IOException;
    void purge() throws IOException;
    void write(byte[] data, int timeoutMillis, CancelSignal cancel) throws IOException;
    byte[] read(int maximumBytes, int timeoutMillis) throws IOException;
    @Override void close();
}
