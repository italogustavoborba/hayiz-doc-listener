package br.com.wlabs.hayiz.doc.listener.cache;

import br.com.wlabs.hayiz.doc.listener.cache.model.Certificate;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.HashMap;

public final class CertificateCache {

    private static HashMap<String, Certificate> cache = new HashMap<>();

    public static void set(String key, Certificate object) {
        cache.put(key, object);
    }

    @Nullable
    public static Certificate get(String key) {
        Certificate certificate = cache.get(key);
        if (certificate == null) {
            return null;
        }

        if(certificate.getExpiration().isBefore(LocalDateTime.now())) {
            cache.remove(key);
            return null;
        }
        return certificate;
    }

    public static void replace(String key, int exp, Certificate object) {
        cache.put(key, object);
    }
}
