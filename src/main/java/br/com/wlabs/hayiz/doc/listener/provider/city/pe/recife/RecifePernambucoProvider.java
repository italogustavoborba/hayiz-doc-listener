package br.com.wlabs.hayiz.doc.listener.provider.city.pe.recife;

import br.com.wlabs.hayiz.doc.listener.exception.CaptchaException;
import br.com.wlabs.hayiz.doc.listener.provider.Provider;
import br.com.wlabs.hayiz.doc.listener.util.HTTPUtil;
import com.twocaptcha.TwoCaptcha;
import com.twocaptcha.captcha.Captcha;
import okhttp3.*;
import okhttp3.logging.HttpLoggingInterceptor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class RecifePernambucoProvider extends Provider {

    private Logger log = LoggerFactory.getLogger(RecifePernambucoProvider.class);

    protected OkHttpClient buildClient(Collection<Cookie> allCookies) throws Exception {
        ExceptionCatchingExecutor executor = new ExceptionCatchingExecutor();

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return new java.security.cert.X509Certificate[]{};
                    }
                }
        };

        SSLContext sslContext = SSLContext.getInstance("SSL");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

        return new OkHttpClient().newBuilder()
                .readTimeout(60, TimeUnit.SECONDS)
                .connectTimeout(60 / 2, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                //.followRedirects(false)
                //.dispatcher(new Dispatcher(executor))
                .cache(null)
                //.addInterceptor(this.delayInterceptor(1000L, TimeUnit.MILLISECONDS))
                //.addInterceptor(logging)
                .addInterceptor(this.defaultInterceptor())
                .addInterceptor(this.retryInterceptor())
                .cookieJar(cookieJar((allCookies)))
                .sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCerts[0])
                .hostnameVerifier((hostname, session) -> true)
                .build();
    }

    public void validateResponse(Response response) throws Exception {
        if(Objects.isNull(response) || response.isRedirect()) return;

        MediaType mediaType = response.body().contentType();
        if(mediaType.type().equalsIgnoreCase("application")
                && mediaType.subtype().equalsIgnoreCase("octet-stream")) {
            return;
        }

        String data = HTTPUtil.bodyToString(response,
                (Objects.nonNull(mediaType.charset()) ? mediaType.charset() : StandardCharsets.ISO_8859_1));
        Document doc = Jsoup.parse(data);

        if(doc.text().toLowerCase().indexOf("ocorreu problema técnico na sua solicitação") != -1) {
            throw new Exception("500 - Ocorreu problema técnico na sua solicitação. Favor tentar novamente mais tarde. Caso o problema persista, favor enviar mensagem por meio do FALE CONOSCO (/faleconoscoSefin/)");
        }

        if(doc.text().toLowerCase().indexOf("no momento nossos serviços estão indisponíveis") != -1) {
            throw new Exception("500 - No momento nossos serviços estão indisponíveis");
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
