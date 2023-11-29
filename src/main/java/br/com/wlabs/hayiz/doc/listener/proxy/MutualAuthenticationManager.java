package br.com.wlabs.hayiz.doc.listener.proxy;

import io.netty.handler.codec.http.HttpRequest;
import okhttp3.tls.HandshakeCertificates;
import org.littleshoot.proxy.MitmManager;

import javax.net.ssl.*;
import java.security.SecureRandom;

public class MutualAuthenticationManager implements MitmManager {

    private final HandshakeCertificates handshakeCertificates;

    public MutualAuthenticationManager(HandshakeCertificates handshakeCertificates) {
        this.handshakeCertificates = handshakeCertificates;
    }

    @Override
    public SSLEngine serverSslEngine(String peerHost, int peerPort) {
        return handshakeCertificates.sslContext().createSSLEngine(peerHost, peerPort);
    }

    @Override
    public SSLEngine serverSslEngine() {
        return handshakeCertificates.sslContext().createSSLEngine();
    }

    @Override
    public SSLEngine clientSslEngineFor(HttpRequest httpRequest, SSLSession serverSslSession) {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            TrustManager[] trustManagers = new TrustManager[]{ handshakeCertificates.trustManager() };
            KeyManager[] keyManagers = new KeyManager[] { handshakeCertificates.keyManager() } ;
            sslContext.init(keyManagers, trustManagers, new SecureRandom());
            return sslContext.createSSLEngine();
        } catch (Exception e) {
            throw new IllegalStateException("Error setting SSL facing server", e);
        }
    }
}
