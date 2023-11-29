package br.com.wlabs.hayiz.doc.listener.exception;

public class CaptchaException extends Throwable {

    static final long serialVersionUID = 7818375828146090155L;

    public CaptchaException() {
        super();
    }

    public CaptchaException(String message) {
        super(message);
    }

    public CaptchaException(String message, Throwable cause) {
        super(message, cause);
    }

    public CaptchaException(Throwable cause) {
        super(cause);
    }
}
