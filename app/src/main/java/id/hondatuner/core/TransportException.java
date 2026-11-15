package id.hondatuner.core;

/** A failed USB channel or uncertain transmission; a new connection is required. */
public final class TransportException extends ProtocolException {
    private static final long serialVersionUID = 1L;
    public TransportException(String message) { super(message); }
    public TransportException(String message, Throwable cause) { super(message); initCause(cause); }
}
