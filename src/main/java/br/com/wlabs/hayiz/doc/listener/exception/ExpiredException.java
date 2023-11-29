package br.com.wlabs.hayiz.doc.listener.exception;

public class ExpiredException extends Throwable {

    static final long serialVersionUID = 7818375828146090155L;

    public ExpiredException() {
        super();
    }

    public ExpiredException(String message) {
        super(message);
    }

    public ExpiredException(String message, Throwable cause) {
        super(message, cause);
    }

    public ExpiredException(Throwable cause) {
        super(cause);
    }
}
