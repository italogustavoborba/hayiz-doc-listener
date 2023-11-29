package br.com.wlabs.hayiz.doc.listener.exception;

public class TemporaryException extends Throwable {

    static final long serialVersionUID = 7818375828146090155L;

    public TemporaryException() {
        super();
    }

    public TemporaryException(String message) {
        super(message);
    }

    public TemporaryException(String message, Throwable cause) {
        super(message, cause);
    }

    public TemporaryException(Throwable cause) {
        super(cause);
    }
}
