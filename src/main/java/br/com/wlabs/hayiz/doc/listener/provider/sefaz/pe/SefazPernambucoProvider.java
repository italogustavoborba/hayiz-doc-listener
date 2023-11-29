package br.com.wlabs.hayiz.doc.listener.provider.sefaz.pe;

import br.com.wlabs.hayiz.doc.listener.enumerator.SessionType;
import br.com.wlabs.hayiz.doc.listener.provider.Provider;
import br.com.wlabs.hayiz.doc.listener.session.SessionManagement;
import br.com.wlabs.hayiz.doc.listener.session.model.Invoke;
import br.com.wlabs.hayiz.doc.listener.session.model.Session;
import br.com.wlabs.hayiz.doc.listener.util.HTMLUtil;
import br.com.wlabs.hayiz.doc.listener.util.HTTPUtil;
import br.com.wlabs.hayiz.doc.listener.util.ProxyUtil;
import okhttp3.*;
import okhttp3.logging.HttpLoggingInterceptor;
import okhttp3.tls.HandshakeCertificates;
import org.apache.commons.io.FilenameUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.FormElement;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class SefazPernambucoProvider extends Provider {

    private Logger log = LoggerFactory.getLogger(SefazPernambucoProvider.class);

    protected Map<String, String> login(OkHttpClient httpClient, Collection<Cookie> allCookies, String certificateHash,
                                        String certificateKey, String certificatePassword) throws Exception {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("certificateKey", certificateKey);
        parameters.put("certificatePassword", certificatePassword);

        Session session = SessionManagement.invoke(Invoke.builder()
                .key(certificateHash)
                .type(SessionType.SEFAZ_PE.name())
                .parameters(parameters)
                .build());
        if(Objects.isNull(session.getData()) ||  session.getData().isEmpty()) {
            SessionManagement.revoke(session.getId());
            throw new Exception(LocalDateTime.now() + " Session Management Failed");
        }

        allCookies.addAll(session.getData().stream()
                .map(cookie -> new Cookie.Builder()
                            .name(cookie.getName())
                            .value(cookie.getValue())
                            .expiresAt(cookie.getExpiresAt())
                            .domain(cookie.getDomain())
                            .path(cookie.getPath())
                            .build())
                .collect(Collectors.toList()));

        Request request = new Request.Builder()
                .url("https://efisco.sefaz.pe.gov.br/sfi_com_sca/PRManterMenuAcesso")
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            MediaType mediaType = response.body().contentType();
            String url = response.request().url().toString();
            if(!url.contains("https://efisco.sefaz.pe.gov.br/sfi_com_sca/")) {
                SessionManagement.revoke(session.getId());
                throw new Exception(LocalDateTime.now() + " SefazPernambucoProvider::login failed: " + url);
            }
            Document doc = Jsoup.parse(response.body().byteStream(), mediaType.charset().name(),
                    "https://efisco.sefaz.pe.gov.br/sfi/");
            Map<String, String> formData = HTMLUtil.formElementToMap((FormElement) doc.selectFirst("form[name=frm_principal]"));
            if (Objects.isNull(formData) && !formData.isEmpty()) {
                SessionManagement.revoke(session.getId());
                throw new Exception(LocalDateTime.now() + " SefazPernambucoProvider::login GOV failed: " + url);
            }
            log.debug(LocalDateTime.now() + " SefazPernambucoProvider::login GOV success");
            return formData;
        }
    }

    protected Map<String, String> login(OkHttpClient httpClient, String cpf) throws Exception {
        MediaType mediaType = MediaType.parse("application/x-www-form-urlencoded");
        RequestBody body = RequestBody.create("evento=processarLogin"
                        + "&Login=" + cpf.replaceAll("(\\d{2})(\\d{3})(\\d{3})(\\d{4})(\\d{2})", "$1.$2.$3-$4"),
                mediaType);
        Request request = new Request.Builder()
                .url("https://efiscoi.sefaz.pe.gov.br/sfi_com_sca/PRGerenciarLoginUsuario")
                .method("POST", body)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            mediaType = response.body().contentType();
            Document doc = Jsoup.parse(response.body().byteStream(), mediaType.charset().name(),
                    "https://efisco.sefaz.pe.gov.br/sfi/");
            Map<String, String> formData = HTMLUtil.formElementToMap((FormElement) doc.selectFirst("form[name=frm_principal]"));
            if (Objects.isNull(formData) && !formData.isEmpty()) {
                throw new Exception(LocalDateTime.now() + " SefazPEAction::login(httpClient = " + httpClient + ", cpf = " + cpf + ") => failed");
            }
            if(!formData.containsKey("cd_usuario")
                    || formData.get("cd_usuario").isEmpty()
                    || formData.get("cd_usuario").equalsIgnoreCase("1")) {
                throw new Exception(LocalDateTime.now() + " SefazPEAction::login(httpClient = " + httpClient + ", cpf = " + cpf + ") => failed");
            }
            log.debug(LocalDateTime.now() + " SefazPEAction::login(httpClient = " + httpClient + ", cpf = " + cpf + ") => success");
            return formData;
        }
    }

    protected void logout(OkHttpClient client, Map<String, String> formData) {
        try {
            Request request = new Request.Builder()
                    .url("https://efiscoi.sefaz.pe.gov.br/sfi_com_sca/PREfetuarLogout")
                    .method("POST", HTMLUtil.mapToFormBody(formData).build())
                    .addHeader("Content-Type", "application/x-www-form-urlencoded")
                    .build();
            client.newCall(request).execute();
        } catch (Exception e) {
            e.printStackTrace();
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

    protected OkHttpClient buildClientPublic(Collection<Cookie> allCookies) throws Exception {
        return new OkHttpClient().newBuilder()
                .callTimeout(Duration.ofSeconds(60))
                .readTimeout(Duration.ofSeconds(60))
                .connectTimeout(Duration.ofSeconds(60))
                .writeTimeout(Duration.ofSeconds(60))
                .cache(null)
                .addInterceptor(this.delayInterceptor(1000L, TimeUnit.MILLISECONDS))
                .addInterceptor(this.defaultInterceptor())
                .addInterceptor(this.retryInterceptor())
                .cookieJar(cookieJar((allCookies)))
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

        Element message = doc.selectFirst("td[style=\"padding-left: 10;\"]");
        if(message != null && message.hasText() && message.text().length() > 5) {
            if(message.text().toLowerCase().indexOf("tente novamente") != -1) {
                throw new Exception(message.text());
            }
            if(message.text().toLowerCase().indexOf("em execução") != -1) {
                throw new Exception(message.text());
            }
            if(message.text().toLowerCase().indexOf("inatividade") != -1) {
                throw new Exception(message.text());
            }

            /*if(message.text().toLowerCase().indexOf("acesso não autorizado") != -1) {
                throw new Exception(message.text());
            }*/

            if(message.text().toLowerCase().indexOf("não foi possível") != -1) {
                throw new Exception(message.text());
            }
        }

        Element error = doc.selectFirst("div[id=\"msgErro\"]");
        if(error != null && error.hasText() && error.text().length() > 5) {
            if(error.text().toLowerCase().indexOf("captcha") != -1) {
                throw new Exception(error.text());
            }
        }
    }

    protected void fixCSS(Element content) {
        Element head = content.root().selectFirst("head");
        head.empty();

        String[] css = new String[]{"http://assets.hayizsis.com.br/efisco.sefaz.pe.gov.br/css/sefaz_pe.css",
                "http://assets.hayizsis.com.br/efisco.sefaz.pe.gov.br/css/swiper-bundle.min.css",
                "http://assets.hayizsis.com.br/efisco.sefaz.pe.gov.br/css/sefaz_pe_variaveis.css",
                "http://assets.hayizsis.com.br/efisco.sefaz.pe.gov.br/css/sefaz_pe_sidebar.css",
                "http://assets.hayizsis.com.br/efisco.sefaz.pe.gov.br/css/sefaz_pe_tema.css",
                "http://assets.hayizsis.com.br/efisco.sefaz.pe.gov.br/css/jquery-ui.min.css",
                "http://assets.hayizsis.com.br/efisco.sefaz.pe.gov.br/css/sefaz_pe_interface_rica.css",
                "http://assets.hayizsis.com.br/efisco.sefaz.pe.gov.br/css/bootstrap.min.css",
                "http://assets.hayizsis.com.br/efisco.sefaz.pe.gov.br/css/all.min.css"};

        Arrays.stream(css).forEach(url -> {
            head.appendElement("link")
                    .attr("href", url)
                    .attr("rel", "stylesheet")
                    .attr("type", "text/css");
        });

        Elements images = content.root().select("img");
        images.forEach(image -> {
            String src = image.attr("src");
            String name = FilenameUtils.getName(src);
            image.attr("src", "http://assets.hayizsis.com.br/efisco.sefaz.pe.gov.br/images/" + name);
        });

        //Elements inputs = content.root().select("input[type=HIDDEN]");
        //inputs.remove();
    }

    protected void fixDoc(Document doc, String baseUri) throws MalformedURLException {
        Elements stylesheets = doc.root().select("link");
        for(Element stylesheet : stylesheets) {
            String href = stylesheet.attr("href");
            if(Objects.nonNull(href) && !href.isEmpty())
                stylesheet.attr("href", new URL(baseUri).getHost() + href);
        }

        Elements images = doc.root().select("img");
        for(Element image : images) {
            String src = image.attr("src");
            if(Objects.nonNull(src) && !src.isEmpty())
                image.attr("src", new URL(baseUri).getHost() + src);
        }

        Elements scripts = doc.root().select("script");
        for(Element script : scripts) {
            String src = script.attr("src");
            if(Objects.nonNull(src) && !src.isEmpty())
                script.attr("src", new URL(baseUri).getHost() + src);
        }
    }

    protected Thread keepSession(OkHttpClient client, Map<String, String> loginFormData, long duration, TimeUnit timeUnit) {
        final long delay = timeUnit.toMillis(duration);
        return new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    client.newCall(new Request.Builder()
                            .url("https://efisco.sefaz.pe.gov.br/sfi_com_sca/PRMontarMenuAcesso")
                            .method("POST", HTMLUtil.mapToFormBody(loginFormData).build())
                            .addHeader("Content-Type", "application/x-www-form-urlencoded")
                            .build()).execute();
                    Thread.sleep(delay);
                } catch (Exception exception) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }
}
