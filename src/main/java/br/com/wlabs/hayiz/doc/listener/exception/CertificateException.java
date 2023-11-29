package br.com.wlabs.hayiz.doc.listener.exception;

public class CertificateException extends Throwable {

    static final long serialVersionUID = 7818375828146090155L;

    public CertificateException() {
        super();
    }

    public CertificateException(String message) {
        super(message);
    }

    public CertificateException(String message, Throwable cause) {
        super(message, cause);
    }

    public CertificateException(Throwable cause) {
        super(cause);
    }
}
