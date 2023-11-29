package br.com.wlabs.hayiz.doc.listener.exception;

public class LoginException extends Throwable {

    static final long serialVersionUID = 7818375828146090155L;

    public LoginException() {
        super();
    }

    public LoginException(String message) {
        super(message);
    }

    public LoginException(String message, Throwable cause) {
        super(message, cause);
    }

    public LoginException(Throwable cause) {
        super(cause);
    }
}
