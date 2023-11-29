package br.com.wlabs.hayiz.doc.listener.provider.city.pe.garanhuns;

import br.com.wlabs.hayiz.doc.listener.provider.Provider;
import br.com.wlabs.hayiz.doc.listener.util.HTMLUtil;
import br.com.wlabs.hayiz.doc.listener.util.HTTPUtil;
import okhttp3.*;
import okhttp3.logging.HttpLoggingInterceptor;
import okhttp3.tls.HandshakeCertificates;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.FormElement;

import java.security.KeyStore;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class GaranhunsProvider extends Provider {

    protected Map<String, String> login(OkHttpClient httpClient) throws Exception {
        Request request = new Request.Builder()
                .url("https://www.tinus.com.br/tcf/logcert.aspx")
                .method("GET", null)
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            MediaType mediaType = response.body().contentType();
            Document doc = Jsoup.parse(response.body().byteStream(), mediaType.charset().name(),
                    "https://www.tinus.com.br");
            return null;
        }
    }

    protected OkHttpClient buildClient(Collection<Cookie> allCookies, KeyStore.PrivateKeyEntry keyEntry) throws Exception {
        HandshakeCertificates handshakeCertificates = HTTPUtil.buildSSL(keyEntry);

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        ExceptionCatchingExecutor executor = new ExceptionCatchingExecutor();

        return new OkHttpClient().newBuilder()
                .readTimeout(60, TimeUnit.SECONDS)
                .connectTimeout(60 / 2, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                //.proxySelector(ProxyUtil.proxySelector())
                .dispatcher(new Dispatcher(executor))
                .cache(null)
                //.addNetworkInterceptor(logging)
                .addInterceptor(this.delayInterceptor(1000L, TimeUnit.MILLISECONDS))
                .addInterceptor(this.defaultInterceptor())
                .addInterceptor(this.retryInterceptor())
                .sslSocketFactory(handshakeCertificates.sslSocketFactory(), handshakeCertificates.trustManager())
                .cookieJar(cookieJar((allCookies)))
                .build();
    }

    @Override
    protected void validateResponse(Response response) throws Exception {

    }
}
