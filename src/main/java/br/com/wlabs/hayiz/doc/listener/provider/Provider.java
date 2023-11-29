package br.com.wlabs.hayiz.doc.listener.provider;

import br.com.wlabs.hayiz.doc.listener.exception.CaptchaException;
import br.com.wlabs.hayiz.doc.listener.exception.CertificateException;
import br.com.wlabs.hayiz.doc.listener.util.*;
import com.twocaptcha.TwoCaptcha;
import com.twocaptcha.captcha.Captcha;
import io.netty.handler.codec.http.HttpRequest;
import okhttp3.Cookie;
import okhttp3.*;
import okhttp3.tls.HandshakeCertificates;
import org.apache.commons.io.FileUtils;
import org.littleshoot.proxy.HttpProxyServer;
import org.littleshoot.proxy.HttpProxyServerBootstrap;
import org.littleshoot.proxy.MitmManager;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.HasDevTools;
import org.openqa.selenium.devtools.v109.page.Page;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import static java.util.stream.Collectors.toList;

public abstract class Provider {

    private Logger log = LoggerFactory.getLogger(Provider.class);
    private String userAgent = UserAgentUtil.randomUserAgent();

    protected KeyStore.PrivateKeyEntry buildKeyEntry(String certificateKey, String certificatePassword)
            throws CertificateException {
        try {
            InputStream inputStream = StorageUtil.getObject(certificateKey);
            KeyStore.PrivateKeyEntry keyEntry = CertificateUtil.buildCert(inputStream, certificatePassword);
            return keyEntry;
        } catch (Exception exception) {
            log.error(LocalDateTime.now() + " Provider::buildKeyEntry(certificateKey = " + certificateKey + ", certificatePassword = *******) " + exception.getMessage() );
            throw new CertificateException(exception.getMessage(), exception);
        }
    }

    protected CookieJar cookieJar(Collection<Cookie> allCookies) {
        return new CookieJar() {
            @Override
            public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                List<String> names = cookies.stream().map(Cookie::name)
                        .collect(toList());
                allCookies.removeIf(cookie -> names.contains(cookie.name()));
                allCookies.addAll(cookies);
            }

            @Override
            public List<Cookie> loadForRequest(HttpUrl url) {
                return allCookies
                        .stream()
                        .collect(toList());
            }
        };
    }

    protected Interceptor retryInterceptor() {
        return (chain) -> {
            final int MAX_RETRIES = 10;
            final int RETRY_TIME = 5000;

            Request request = chain.request();
            Response response = null;
            int retriesCount = 0;
            do {
                try {
                    response = chain.proceed(request);
                    /*if (!response.isSuccessful()) {
                        if(Objects.nonNull(response)) {
                            response.close();
                        }
                        throw new IOException(LocalDateTime.now() + " Failed " + request.method() + "=>" + request.url() + "\n" +
                                "Body: " + HTTPUtil.bodyToString(request) + "\n" +
                                "-----------------------------------------------------------------------------");
                    }*/
                    validateResponse(response);
                } catch (Exception e) {
                    if(e.getMessage().contains("Canceled") ||
                            e.getMessage().contains("Socket closed") || e.getMessage().contains("Connection reset")) {
                        if(Objects.nonNull(response)) {
                            response.close();
                        }
                        throw new IOException(LocalDateTime.now() + " SefazPEAction::retryInterceptor() => " + e.getMessage() + " " + request.method() + "=>" + request.url() + "\n" +
                                "Body: " + HTTPUtil.bodyToString(request) + "\n" +
                                "-----------------------------------------------------------------------------");
                    }
                    log.error(LocalDateTime.now() + " Retrying " + request.method() + "=>" + request.url() + "\n" +
                            "Body: " + HTTPUtil.bodyToString(request) + "\n" +
                            "Retry: " + retriesCount + ": " + e + "\n" +
                            "-----------------------------------------------------------------------------");
                    retriesCount++;

                    try {
                        Thread.sleep(RETRY_TIME);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    } catch (Exception exception) {

                    }

                    if(Objects.nonNull(response)) {
                        response.close();
                    }
                    response = null;
                }
            } while (response == null && retriesCount < MAX_RETRIES);
            if(response != null && retriesCount > 0) {
                log.debug(LocalDateTime.now() + " Request success " + request.method() + "=>" + request.url() + "\n" +
                        "Body: " + HTTPUtil.bodyToString(request) + "\n" +
                        "Retry: " + retriesCount + "\n" +
                        "-----------------------------------------------------------------------------");
            }
            if (response == null) {
                throw new IOException(LocalDateTime.now() + " Retry failed " + request.method() + "=>" + request.url() + "\n" +
                        "Body: " + HTTPUtil.bodyToString(request) + "\n" +
                        "Retry: " + retriesCount + "\n" +
                        "-----------------------------------------------------------------------------");
            }
            return response;
        };
    }

    protected Interceptor defaultInterceptor() {
        return (chain) -> {
            Request newRequest = chain.request().newBuilder()
                    .addHeader("User-Agent", this.userAgent)
                    .addHeader("Connection", "keep-alive")
                    .addHeader("Cache-Control", "max-age=0")
                    //.addHeader("Pragma", "no-cache")
                    .build();
            return chain.proceed(newRequest);
        };
    }

    protected Interceptor defaultInterceptor(Map<String, String> headers) {
        return (chain) -> {
            Request newRequest = chain.request().newBuilder()
                    .headers(Headers.of(headers))
                    .build();
            return chain.proceed(newRequest);
        };
    }

    protected Interceptor delayInterceptor(final long duration, final TimeUnit timeUnit) {
        final long delay = timeUnit.toMillis(duration);
        return (chain) -> {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (Exception exception) {

            }
            return chain.proceed(chain.request());
        };
    }

    protected static class ExceptionCatchingExecutor extends ThreadPoolExecutor {
        private final BlockingQueue<Exception> exceptions = new LinkedBlockingQueue<>();

        public ExceptionCatchingExecutor() {
            super(1, 1, 0, TimeUnit.SECONDS, new SynchronousQueue<Runnable>());
        }

        @Override
        public void execute(final Runnable runnable) {
            super.execute(() -> {
                try {
                    runnable.run();
                } catch (Exception e) {
                    exceptions.add(e);
                }
            });
        }

        public Exception takeException() throws InterruptedException {
            return exceptions.take();
        }
    }

    protected HttpProxyServer buildDriverProxySSL(KeyStore.PrivateKeyEntry keyEntry) throws IOException {
        HandshakeCertificates handshakeCertificates = HTTPUtil.buildSSL(keyEntry);
        HttpProxyServerBootstrap httpProxyServerBootstrap = DefaultHttpProxyServer.bootstrap()
                .withName(keyEntry.getCertificate().toString())
                .withTransparent(true)
                .withAllowRequestToOriginServer(true)
                .withAuthenticateSslClients(true)
                .withManInTheMiddle(new MitmManager() {
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
                });

        final int MAX_RETRIES = 1000;
        int retriesCount = 0;
        do {
            try {
                int port = RandomizerUtil.generate(8090, 8190);
                return httpProxyServerBootstrap
                        .withPort(port)
                        .start();
            } catch (Exception exception) {
                retriesCount++;
            }
        } while (retriesCount < MAX_RETRIES);

        throw new IOException("No Port Available");
    }

    protected void loginDriverBySSLGov(RemoteWebDriver remoteWebDriver, String clientId) {
        remoteWebDriver.get("https://certificado.sso.acesso.gov.br/login?client_id=" + clientId);
        new WebDriverWait(remoteWebDriver, Duration.ofSeconds(RandomizerUtil.generate(30, 50)).getSeconds())
                .pollingEvery(Duration.ofSeconds(2))
                .until(p -> p.findElement(By.className("cabecalho_home")));
    }

    protected ChromeDriver buildChromeDriver(String dataDir, Map<String, Object> prefs, int implicitlyWait, boolean headless) throws IOException {
        return buildChromeDriver(dataDir, prefs, implicitlyWait, null, headless);
    }

    protected ChromeDriver buildChromeDriver(String dataDir, Map<String, Object> prefs, int implicitlyWait, Proxy proxy, boolean headless)
            throws IOException {
        ChromeOptions options = new ChromeOptions();
        options.setAcceptInsecureCerts(true);
        if(Objects.nonNull(proxy)) {
            options.setProxy(proxy);
        }
        options.setBrowserVersion(UserAgentUtil.randomUserAgent());

        options.addArguments(
                "--incognito",
                "--no-sandbox",
                "--disable-extensions",
                "--no-default-browser-check",
                "--disable-gl-drawing-for-tests",
                "--disable-blink-features",
                "--disable-blink-features=AutomationControlled",
                "--start-maximized",
                "--disable-infobars",
                "--disable-gpu",
                "--disable-logging",
                "--disable-dev-shm-usage",
                "--window-size=1920,1080",
                "--remote-allow-origins=*",
                "--user-data-dir=" + dataDir + "/user-data");

        options.setExperimentalOption("excludeSwitches", Collections.singletonList("enable-automation"));
        options.setPageLoadStrategy(PageLoadStrategy.NONE);

        String printOptions = "{\"recentDestinations\": " +
                "[{\"id\": \"Save as PDF\", \"origin\": \"local\", \"account\": \"\"}]" +
                ", \"selectedDestinationId\": \"Save as PDF\", \"version\": 2, " +
                "\"isHeaderFooterEnabled\": false, \"isCssBackgroundEnabled\": true, \"customMargins\": {}, " +
                "\"marginsType\": 1}";
        prefs.put("printing.print_preview_sticky_settings.appState", printOptions);
        prefs.put("download.prompt_for_download", false);
        prefs.put("profile.default_content_settings.popups", 0);
        prefs.put("profile.default_content_setting_values.notifications", 1);
        prefs.put("profile.password_manager_enabled", false);
        prefs.put("credentials_enable_service", false);

        options.setExperimentalOption("prefs", prefs);
        options.setHeadless(headless);

        ChromeDriver driver = new ChromeDriver(options);
        driver.manage().window().maximize();
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(20));
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(20));
        driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(20));
        driver.manage().deleteAllCookies();

        /*org.openqa.selenium.remote.http.Route route =
                org.openqa.selenium.remote.http.Route.matching(req -> HttpMethod.GET == req.getMethod())
                .to(() -> req -> new HttpResponse().setContent(Contents.utf8String("Hello, World!")));
         try (NetworkInterceptor interceptor = new NetworkInterceptor(driver, route)) {
            try {
                WebElement img = driver.findElement(By.tagName("img"));
                WebElement answer = driver.findElement(By.name("answer"));
                answer.sendKeys("");
                WebElement jar = driver.findElement(By.id("jar"));
                jar.click();
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        }*/

        return driver;
    }

    public static Collection<File> listFilesWaitFor(final File directory, final String[] extensions, final int seconds) throws IOException {
        Objects.requireNonNull(directory, "directory");
        final long finishAtMillis = System.currentTimeMillis() + (seconds * 1000L);
        boolean wasInterrupted = false;
        long remainingMillis;
        Collection<File> listFiles;
        try {
            do {
                listFiles = FileUtils.listFiles(directory, extensions, false);
                if(!listFiles.isEmpty()) break;

                remainingMillis = finishAtMillis - System.currentTimeMillis();
                try {
                    Thread.sleep(Math.min(100, remainingMillis));
                } catch (final InterruptedException ignore) {
                    wasInterrupted = true;
                } catch (final Exception ex) {
                    break;
                }
            } while (remainingMillis > 0);
        } finally {
            if (wasInterrupted) {
                Thread.currentThread().interrupt();
            }
        }
        return listFiles;
    }

    protected byte[] printToPDF(ChromeDriver chromeDriver, double scale, boolean landscape, boolean displayHeaderFooter,
                           boolean printBackground) {
        /*PrintOptions printOptions = new PrintOptions();
        printOptions.setScale(scale);
        printOptions.setOrientation(landscape ? PrintOptions.Orientation.LANDSCAPE : PrintOptions.Orientation.PORTRAIT);
        printOptions.setPageMargin(new PageMargin(0, 0, 0, 0));
        printOptions.setBackground(printBackground);

        Pdf pdf = ((PrintsPage) chromeDriver).print(printOptions);
        String content =  pdf.getContent();
        return org.apache.commons.net.util.Base64.decodeBase64(content);*/

        DevTools devTools = ((HasDevTools) chromeDriver).getDevTools();

        try {
            devTools.createSessionIfThereIsNotOne();
            Page.PrintToPDFTransferMode transferMode = Page.PrintToPDFTransferMode.RETURNASBASE64;
            Page.PrintToPDFResponse response = devTools.send(Page.printToPDF(
                    Optional.of(landscape), Optional.of(displayHeaderFooter), Optional.of(printBackground),
                    Optional.of(scale), Optional.empty(), Optional.empty(),
                    Optional.of(0), Optional.of(0), Optional.of(0),
                    Optional.of(0), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.of(transferMode)));
            return org.apache.commons.net.util.Base64.decodeBase64(response.getData());
        } finally {
            //devTools.close();
        }
    }

    public void click(WebDriver driver, WebElement element, long timeOutInSeconds) {
        WebDriverWait wait = new WebDriverWait(driver, timeOutInSeconds);
        ExpectedCondition<Boolean> elementIsClickable = arg0 -> {
            try {
                element.click();
                return true;
            } catch (Exception e) {
                return false;
            }
        };
        wait.until(elementIsClickable);
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

    protected abstract void validateResponse(Response response) throws Exception;
}
