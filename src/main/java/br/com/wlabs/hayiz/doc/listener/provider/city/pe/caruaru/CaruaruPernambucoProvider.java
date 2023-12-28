package br.com.wlabs.hayiz.doc.listener.provider.city.pe.caruaru;

import br.com.wlabs.hayiz.doc.listener.provider.Provider;
import br.com.wlabs.hayiz.doc.listener.util.HTTPUtil;
import okhttp3.*;
import okhttp3.logging.HttpLoggingInterceptor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class CaruaruPernambucoProvider extends Provider {

    private Logger log = LoggerFactory.getLogger(CaruaruPernambucoProvider.class);

    protected OkHttpClient buildClient(Collection<Cookie> allCookies) throws Exception {

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        ExceptionCatchingExecutor executor = new ExceptionCatchingExecutor();

        return new OkHttpClient().newBuilder()
                .readTimeout(120, TimeUnit.SECONDS)
                .connectTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .dispatcher(new Dispatcher(executor))
                .cache(null)
                //.addNetworkInterceptor(logging)
                .addInterceptor(this.delayInterceptor(5000L, TimeUnit.MILLISECONDS))
                .addInterceptor(this.defaultInterceptor())
                .addInterceptor(this.retryInterceptor())
                .cookieJar(cookieJar((allCookies)))
                .build();
    }

    public void validateResponse(Response response) throws Exception {
        if(Objects.isNull(response) || response.isRedirect()) return;

        MediaType mediaType = response.body().contentType();
        if(mediaType.type().equalsIgnoreCase("application")
                && (mediaType.subtype().equalsIgnoreCase("octet-stream") ||
                    mediaType.subtype().equalsIgnoreCase("pdf"))) {
            return;
        }

        String data = HTTPUtil.bodyToString(response,
                (Objects.nonNull(mediaType.charset()) ? mediaType.charset() : StandardCharsets.ISO_8859_1));
        Document doc = Jsoup.parse(data);

        if(doc.text().toLowerCase().indexOf("error-message") != -1) {
            throw new Exception("500 - Operação não realizada");
        }

        if(doc.text().toLowerCase().indexOf("internal server error") != -1) {
            throw new Exception("500 - Internal server error");
        }

    }

    protected void fixDoc(Document doc, String baseUri) throws MalformedURLException {
        Elements stylesheets = doc.root().select("link");
        for(Element stylesheet : stylesheets) {
            String href = stylesheet.attr("href");
            if(Objects.nonNull(href) && !href.isEmpty())
                stylesheet.attr("href", baseUri + href);
        }

        Elements images = doc.root().select("img");
        for(Element image : images) {
            String src = image.attr("src");
            if(Objects.nonNull(src) && !src.isEmpty())
                image.attr("src", baseUri + src);
        }

        Elements scripts = doc.root().select("script");
        for(Element script : scripts) {
            String src = script.attr("src");
            if(Objects.nonNull(src) && !src.isEmpty())
                script.attr("src", baseUri + src);
        }
    }
}
