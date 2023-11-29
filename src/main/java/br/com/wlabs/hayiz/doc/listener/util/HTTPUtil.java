package br.com.wlabs.hayiz.doc.listener.util;

import okhttp3.Request;
import okhttp3.Response;
import okhttp3.tls.HandshakeCertificates;
import okhttp3.tls.HeldCertificate;
import okio.Buffer;
import okio.BufferedSource;

import java.io.IOException;
import java.nio.charset.Charset;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;

import static java.util.stream.Collectors.toList;

public class HTTPUtil {

    public static String bodyToString(final Response response, Charset charset) throws IOException {
        try {
            BufferedSource source = response.body().source();
            source.request(Long.MAX_VALUE);
            Buffer buffer = source.getBuffer();
            return buffer.clone().readString(charset);
        } catch (Exception e) {
            //e.printStackTrace();
            return response.body().string();
        } finally {
            //response.body().close();
        }
        /*BufferedSource source = response.body().source();
        source.request(Long.MAX_VALUE);
        Buffer buffer = source.getBuffer();
        return buffer.clone().readString(charset);*/
    }

    public static String bodyToString(final Request request) throws IOException {
        try {
            final Request copy = request.newBuilder().build();
            final Buffer buffer = new Buffer();
            copy.body().writeTo(buffer);
            return buffer.readUtf8();
        } catch (Exception e) {
            //e.printStackTrace();
        }
        return "";
    }

    public static HandshakeCertificates buildSSL(KeyStore.PrivateKeyEntry keyEntry) throws IOException {

        List<String> caIssuers = CertificateUtil.getCaIssuers((X509Certificate) keyEntry.getCertificate());
        List<X509Certificate> x509Certificates = caIssuers.stream()
                .map(CertificateUtil::readCertificatesFromPKCS7)
                .flatMap(Collection::stream)
                .collect(toList());
        X509Certificate[] certificates = x509Certificates.toArray(new X509Certificate[x509Certificates.size()]);

        HandshakeCertificates.Builder builder = new HandshakeCertificates.Builder();
        for (X509Certificate cert : certificates) {
            builder.addTrustedCertificate(cert);
        }

        X509Certificate certificate = (X509Certificate) keyEntry.getCertificate();
        builder.addTrustedCertificate(certificate);
        builder.addPlatformTrustedCertificates();
        builder.heldCertificate(
                new HeldCertificate(new KeyPair(certificate.getPublicKey(), keyEntry.getPrivateKey()), certificate),
                certificates
        );

        return builder.build();
    }
}
