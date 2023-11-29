package br.com.wlabs.hayiz.doc.listener.provider.city.pe.belojardim;

import br.com.wlabs.hayiz.doc.listener.exception.CaptchaException;
import br.com.wlabs.hayiz.doc.listener.provider.Provider;
import com.twocaptcha.TwoCaptcha;
import com.twocaptcha.captcha.Captcha;
import okhttp3.*;
import okhttp3.logging.HttpLoggingInterceptor;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.MalformedURLException;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class BeloJardimPernambucoProvider extends Provider {

    private Logger log = LoggerFactory.getLogger(BeloJardimPernambucoProvider.class);

    protected OkHttpClient buildClient(Collection<Cookie> allCookies) throws Exception {

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        ExceptionCatchingExecutor executor = new ExceptionCatchingExecutor();

        CookieManager cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        JavaNetCookieJar cookieJar = new JavaNetCookieJar(cookieManager);

        return new OkHttpClient().newBuilder()
                .readTimeout(60, TimeUnit.SECONDS)
                .connectTimeout(60 / 2, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .dispatcher(new Dispatcher(executor))
                .cache(null)
                //.addNetworkInterceptor(logging)
                .addInterceptor(this.delayInterceptor(1000L, TimeUnit.MILLISECONDS))
                .addInterceptor(this.defaultInterceptor())
                .addInterceptor(this.retryInterceptor())
                //.cookieJar(cookieJar((allCookies)))
                .cookieJar(cookieJar)
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

    protected void solveCaptcha(Captcha captcha) throws CaptchaException {
        TwoCaptcha twoCaptcha = new TwoCaptcha("1ee77717888701a7fb6336e54f5dc398");
        twoCaptcha.setDefaultTimeout(120);
        twoCaptcha.setRecaptchaTimeout(120);
        twoCaptcha.setPollingInterval(5);

        try {
            if(twoCaptcha.balance() <= 0) {
                throw new CaptchaException("TwoCaptcha Insufficient funds");
            }
            twoCaptcha.solve(captcha);
        } catch (Exception e) {
            throw new CaptchaException(e);
        }
    }
}
