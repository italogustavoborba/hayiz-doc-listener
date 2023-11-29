package br.com.wlabs.hayiz.doc.listener.provider.receitafederal;

import br.com.wlabs.hayiz.doc.listener.exception.CaptchaException;
import br.com.wlabs.hayiz.doc.listener.exception.MessageException;
import br.com.wlabs.hayiz.doc.listener.exception.TemporaryException;
import br.com.wlabs.hayiz.doc.listener.integration.capmonster.Capmonster;
import br.com.wlabs.hayiz.doc.listener.integration.capmonster.model.Captcha;
import br.com.wlabs.hayiz.doc.listener.integration.capmonster.model.HCaptchaTaskProxyless;
import br.com.wlabs.hayiz.doc.listener.properties.ApplicationContextProvider;
import br.com.wlabs.hayiz.doc.listener.provider.Provider;
import com.twocaptcha.TwoCaptcha;
import com.twocaptcha.captcha.Normal;
import lombok.Data;
import okhttp3.*;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static java.util.stream.Collectors.toList;

public @Data class ReceitaFederalProvider extends Provider {

    private Logger log = LoggerFactory.getLogger(ReceitaFederalProvider.class);
    private Set<Cookie> allCookies = Collections.synchronizedSet(new HashSet<>());

    protected void loginECAC(ChromeDriver driver) throws IOException, CaptchaException {
        driver.get("https://cav.receita.fazenda.gov.br/ecac/");

        this.solveCaptcha(driver);

        ZoneId zoneId = ZoneId.of("America/Sao_Paulo");
        boolean isBlockWithCaptcha = isBlockWithCaptcha(LocalTime.now(zoneId));

        if(isBlockWithCaptcha) {
            WebElement hCaptcha = driver.findElement(By.xpath("//div[@id='hcaptcha-govbr']/textarea"));
            driver.executeScript("arguments[0].setAttribute('style', arguments[1]);", hCaptcha, "display: inline-block");

            HCaptchaTaskProxyless hCaptchaTaskProxyless = new HCaptchaTaskProxyless();
            hCaptchaTaskProxyless.setWebsiteKey("903db64c-2422-4230-a22e-5645634d893f");
            hCaptchaTaskProxyless.setInvisible(true);
            hCaptchaTaskProxyless.setWebsiteURL(driver.getCurrentUrl());

            String userAgent = (String) driver.executeScript("return navigator.userAgent");
            hCaptchaTaskProxyless.setUserAgent(userAgent);

            solveCaptcha(hCaptchaTaskProxyless);

            WebElement hCaptchaGovBr = driver.findElement(By.id("hcaptcha-govbr"));
            WebElement textarea = hCaptchaGovBr.findElement(By.tagName("textarea"));
            textarea.sendKeys(hCaptchaTaskProxyless.getSolution().get("gRecaptchaResponse").toString());
        }

        WebElement login = driver.findElement(By.id("frmLoginCert"));
        login.submit();

        this.solveCaptcha(driver);

        Duration implicitlyWait = driver.manage().timeouts().getImplicitWaitTimeout();
        try {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));
            WebElement certDigital = driver.findElement(By.id("cert-digital"));
            WebElement certDigitalLink = certDigital.findElement(By.tagName("a"));
            driver.get(certDigitalLink.getAttribute("href"));
        } catch (Exception exception) {
            //
        } finally {
            driver.manage().timeouts().implicitlyWait(implicitlyWait);
        }

        new WebDriverWait(driver, Duration.ofSeconds(20).getSeconds())
                .until(ExpectedConditions.presenceOfElementLocated(By.id("informacao-perfil")));

        this.solveCaptcha(driver);
    }

    protected void perfilECAC(ChromeDriver driver, String registerCode) throws MessageException, IOException,
            CaptchaException, TemporaryException {
        WebElement btnPerfil = driver.findElement(By.id("btnPerfil"));
        btnPerfil.click();
        //this.click(driver, btnPerfil, 5000L);

        WebElement formPJ = driver.findElement(By.id("formPJ"));

        ZoneId zoneId = ZoneId.of("America/Sao_Paulo");
        boolean isBlockWithCaptcha = isBlockWithCaptcha(LocalTime.now(zoneId));

        if(isBlockWithCaptcha) {
            WebElement hCaptcha = driver.findElement(By.xpath("//div[@id='hcaptcha-formPJ']/textarea"));
            driver.executeScript("arguments[0].setAttribute('style', arguments[1]);", hCaptcha, "display: inline-block");

            HCaptchaTaskProxyless hCaptchaTaskProxyless = new HCaptchaTaskProxyless();
            hCaptchaTaskProxyless.setWebsiteKey("48378d4b-eb31-409e-904d-e0c3f0aaa655");
            hCaptchaTaskProxyless.setInvisible(true);
            hCaptchaTaskProxyless.setWebsiteURL(driver.getCurrentUrl());
            String userAgent = (String) driver.executeScript("return navigator.userAgent");
            hCaptchaTaskProxyless.setUserAgent(userAgent);

            solveCaptcha(hCaptchaTaskProxyless);

            WebElement hcaptchFormPJ = driver.findElement(By.id("hcaptcha-formPJ"));
            WebElement textarea = hcaptchFormPJ.findElement(By.tagName("textarea"));
            textarea.sendKeys(hCaptchaTaskProxyless.getSolution().get("gRecaptchaResponse").toString());
        }

        driver.findElement(By.id("txtNIPapel2")).sendKeys(registerCode);
        driver.executeScript("submitProcuradorPJ();");

        try {
            new WebDriverWait(driver, Duration.ofSeconds(10).getSeconds())
                    .until(ExpectedConditions.invisibilityOfAllElements(formPJ));
        } catch (Exception exception) {
            WebElement mensagemErro = driver.findElement(By.className("mensagemErro"));
            validateResponse(mensagemErro.getText());
            throw new MessageException(mensagemErro.getText());
        }

        driver.manage().addCookie(new org.openqa.selenium.Cookie("ecac_cxpostal_aviso_fechado", "1", "/"));
        driver.get("https://cav.receita.fazenda.gov.br/ecac/");
    }

    protected void logout(ChromeDriver driver) {
        driver.getLocalStorage().clear();
        driver.getSessionStorage().clear();
        driver.manage().deleteAllCookies();
        //driver.close();
    }

    protected OkHttpClient buildClientPublic() throws Exception {

        return new OkHttpClient().newBuilder()
                .callTimeout(Duration.ofSeconds(60))
                .readTimeout(Duration.ofSeconds(60))
                .connectTimeout(Duration.ofSeconds(60))
                .writeTimeout(Duration.ofSeconds(60))
                .cache(null)
                .addInterceptor(this.delayInterceptor(1000L, TimeUnit.MILLISECONDS))
                .addInterceptor(this.defaultInterceptor())
                .addInterceptor(this.retryInterceptor())
                .cookieJar(new CookieJar() {
                    @Override
                    public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                        allCookies.addAll(cookies);
                    }

                    @Override
                    public List<Cookie> loadForRequest(HttpUrl url) {
                        return allCookies
                                .stream()
                                .collect(toList());
                    }
                })
                .build();
    }

    public void validateResponse(String response) throws TemporaryException {
        if(Objects.isNull(response)) {
            throw new TemporaryException("Retorno desconhecido");
        }

        if(response.trim().isEmpty()) {
            throw new TemporaryException("Retorno desconhecido");
        }

        if(response.toLowerCase().indexOf("sqlada") != -1) {
            throw new TemporaryException(response);
        }
        if(response.toLowerCase().indexOf("rotina natural") != -1) {
            throw new TemporaryException(response);
        }
        if(response.toLowerCase().indexOf("ocorreu um erro") != -1) {
            throw new TemporaryException(response);
        }
        if(response.toLowerCase().indexOf("tente novamente") != -1) {
            throw new TemporaryException(response);
        }
        if(response.toLowerCase().indexOf("sistema temporariamente indisponível") != -1) {
            throw new TemporaryException(response);
        }
        if(response.toLowerCase().indexOf("acesso automatizado") != -1) {
            throw new TemporaryException(response);
        }
        if(response.toLowerCase().indexOf("erro de retorno desconhecido") != -1) {
            throw new TemporaryException(response);
        }
        if(response.toLowerCase().indexOf("undefined") != -1) {
            throw new TemporaryException(response);
        }
    }

    protected void solveCaptcha(Captcha captcha) throws IOException, CaptchaException {
        String apiKey = ApplicationContextProvider.getEnvironmentProperty("hayiz.captcha.capmonster.api-key",
                String.class, "");
        Capmonster capmonster = new Capmonster("44372859b45167fa9160232e214d0d9e");
        capmonster.setDefaultTimeout(120);
        capmonster.setPollingInterval(5);
        capmonster.solve(captcha);
    }

    protected boolean isBlockWithCaptcha(LocalTime now) {
        LocalTime startDate = LocalTime.of(8, 0,0);
        LocalTime endDate = LocalTime.of(18, 0,0);
        return !(now.isBefore(startDate) || now.isAfter(endDate));
    }

    protected boolean isBlockWithMessage(ChromeDriver driver) {
        driver.get("https://cav.receita.fazenda.gov.br/ecac/");
        try {
            Boolean bloqueioCaixaPostalAtivo = (Boolean) driver.executeScript("return window.bloqueioCaixaPostalAtivo");
            if(Objects.nonNull(bloqueioCaixaPostalAtivo)) {
                return bloqueioCaixaPostalAtivo;
            }
            WebElement btnPerfil = driver.findElement(By.id("btnPerfil"));
            return !btnPerfil.isEnabled();
        } catch (Exception exception) {
            return true;
        }
    }

    protected void solveCaptcha(ChromeDriver driver) throws CaptchaException {
        Duration implicitlyWait = driver.manage().timeouts().getImplicitWaitTimeout();
        try {
            //ExpectedConditions.presenceOfElementLocated(By.name("answer"));
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));
            WebElement answer = driver.findElement(By.name("answer"));
            //driver.quit();

            WebElement img = driver.findElement(By.tagName("img"));
            String base64 = img.getAttribute("src");
            base64 = base64.substring(22);

            Normal captcha = new Normal();
            captcha.setBase64(base64);
            captcha.setMinLen(5);
            captcha.setMaxLen(6);
            //captcha.setLang("pt");

            //System.out.println(base64);

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

            /*ImageToTextTask imageToTextTask = new ImageToTextTask();
            imageToTextTask.setBody(base64);

            solveCaptcha(imageToTextTask);*/

            answer.sendKeys(captcha.getCode());
            WebElement jar = driver.findElement(By.id("jar"));
            this.click(driver, jar, 5000);

            //answer = driver.findElement(By.name("answer"));
            //if(answer.isDisplayed()) throw new CaptchaException();

        } catch (Exception exception) {
            //TODO
        } finally {
            driver.manage().timeouts().implicitlyWait(implicitlyWait);
        }
    }


    @Override
    protected void validateResponse(Response response) throws Exception {

    }
}
