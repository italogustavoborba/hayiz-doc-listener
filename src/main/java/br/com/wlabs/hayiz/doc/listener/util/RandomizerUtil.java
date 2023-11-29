package br.com.wlabs.hayiz.doc.listener.util;

public class RandomizerUtil {

    public static int generate(int min, int max) {
        return min + (int) (Math.random() * ((max - min) + 1));
    }
}
