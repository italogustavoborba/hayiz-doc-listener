package br.com.wlabs.hayiz.doc.listener.exception;

public class MessageException extends Throwable {

    static final long serialVersionUID = 7818375828146090155L;

    public MessageException() {
        super();
    }

    public MessageException(String message) {
        super(message);
    }

    public MessageException(String message, Throwable cause) {
        super(message, cause);
    }

    public MessageException(Throwable cause) {
        super(cause);
    }
}
