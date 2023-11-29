package br.com.wlabs.hayiz.doc.listener.provider.receitafederal.action;

import br.com.wlabs.hayiz.doc.listener.exception.*;
import br.com.wlabs.hayiz.doc.listener.listener.SQSAction;
import br.com.wlabs.hayiz.doc.listener.model.Message;
import br.com.wlabs.hayiz.doc.listener.provider.receitafederal.ReceitaFederalProvider;
import br.com.wlabs.hayiz.doc.listener.service.PdfService;
import br.com.wlabs.hayiz.doc.listener.util.SQSUtil;
import br.com.wlabs.hayiz.doc.listener.util.StorageUtil;
import org.apache.commons.io.FileUtils;
import org.littleshoot.proxy.HttpProxyServer;
import org.openqa.selenium.By;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.aws.messaging.listener.Acknowledgment;
import org.springframework.util.StopWatch;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class ListarMensagem extends ReceitaFederalProvider implements SQSAction {

    private Logger log = LoggerFactory.getLogger(ListarMensagem.class);

    @Override
    public void process(Message message, String queueUrl, final String receiptHandle, String messageGroupId,
                        Acknowledgment acknowledgment) throws Exception {
        HttpProxyServer httpProxyServer = null;
        ChromeDriver driver = null;
        String tmpdir = null;
        try {
            Map<String, Object> data = message.getData();

            if(data.isEmpty()) {
                acknowledgment.acknowledge();
                log.debug("Data is empty: " + message);
                return;
            }

            if(!data.containsKey("documents")) {
                acknowledgment.acknowledge();
                log.debug("Documents is empty: " + message);
                return;
            }

            ZoneId zoneId = ZoneId.of("America/Sao_Paulo");
            LocalDate localDate = LocalDate.now(zoneId);
            if(Objects.nonNull(message.getDate()) && !message.getDate().isEmpty()) {
                if(LocalDate.parse(message.getDate()).isBefore(localDate)) {
                    List<Map<String, Object>> documents = (List<Map<String, Object>>) data.get("documents");
                    for(Map<String, Object> document: documents) {
                        SQSUtil.status(document.get("id").toString(), null, "FAIL",
                                "Não foi possível obter os dados: Sistema da Receita Federal indisponível.",
                                message.getId());
                    }
                    acknowledgment.acknowledge();
                    return;
                }
            }

            System.out.println(LocalDateTime.now() + ": process: " + message.getId() + " -> " + data);

            int visibilityTimeout = 300;
            SQSUtil.changeMessageVisibility(queueUrl, receiptHandle, visibilityTimeout);

            KeyStore.PrivateKeyEntry keyEntry = this.buildKeyEntry((String) data.get("certificateKey"),
                    (String) data.get("certificatePassword"));

            httpProxyServer = buildDriverProxySSL(keyEntry);
            Proxy proxy = new Proxy();
            List<String> noProxies = Arrays.asList("www.receita.fazenda.gov.br", "cav.receita.fazenda.gov.br",
                    "hcaptcha.com", "newassets.hcaptcha.com", "barra.brasil.gov.br", "vlibras.gov.br",
                    "consentimento.acesso.gov.br", "estaleiro.serpro.gov.br",
                    "cdn.dsgovserprodesign.estaleiro.serpro.gov.br", "cdnjs.cloudflare.com", "googletagmanager.com",
                    "google-analytics.com");
            proxy.setNoProxy(noProxies.stream().collect(Collectors.joining(",")));
            proxy.setSslProxy(httpProxyServer.getListenAddress().getHostName() + ":" + httpProxyServer.getListenAddress().getPort());

            boolean isBlockWithCaptcha = isBlockWithCaptcha(LocalTime.now(zoneId));

            Path path = Paths.get(FileUtils.getTempDirectory().getAbsolutePath(), UUID.randomUUID().toString());
            tmpdir = Files.createDirectories(path).toFile().getAbsolutePath();

            Map<String, Object> prefs = new HashMap<>();
            driver = buildChromeDriver(tmpdir, prefs, 20, proxy, false);

            this.loginECAC(driver);

            List<Map<String, Object>> errors = new ArrayList<>();
            List<Map<String, Object>> documents = (List<Map<String, Object>>) data.get("documents");
            for(Map<String, Object> document: documents) {
                StopWatch stopWatch = new StopWatch();
                stopWatch.start();

                String key = data.get("workspaceCode").toString() + "/document/" +
                        document.get("companyCode").toString().replaceAll("[^0-9]", "") + "/" +
                        LocalDate.now().format(DateTimeFormatter.ofPattern("yyMM")) + "/mensagem-ecac-" +
                        UUID.randomUUID() + ".pdf";

                try {
                    driver.get("https://cav.receita.fazenda.gov.br/ecac/");

                    this.solveCaptcha(driver);

                    this.perfilECAC(driver, document.get("companyCode").toString().replaceAll("\\D", ""));

                    driver.get("https://cav.receita.fazenda.gov.br/ecac/Aplicacao.aspx?id=00006");

                    this.solveCaptcha(driver);

                    WebElement frmApp = driver.findElement(By.id("frmApp"));

                    driver.get(frmApp.getAttribute("src"));

                    boolean hasNewMessage = false;
                    Duration implicitlyWait = driver.manage().timeouts().getImplicitWaitTimeout();
                    try {
                        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));
                        WebElement lbMensagensNaoLidas = driver.findElement(By.id("lbMensagensNaoLidas"));
                        String text = lbMensagensNaoLidas.getText();
                        String[] array = text.split(":");
                        if(array.length == 2) {
                            int count = Integer.valueOf(array[1].trim());
                            if(count > 0) {
                                hasNewMessage = true;
                            }
                        }
                    } catch (Exception exception) {
                        //IGNORE
                    } finally {
                        driver.manage().timeouts().implicitlyWait(implicitlyWait);
                    }

                    byte[] printToPDF = printToPDF(driver, 1, false, false, true);
                    byte[] bytes = PdfService.signature(new ByteArrayInputStream(printToPDF), keyEntry);

                    StorageUtil.upload(key, bytes, "application/pdf");
                    SQSUtil.status(document.get("id").toString(), key, (hasNewMessage ? "FOUND_ISSUE" : "DONE"), null, message.getId());

                } catch (MessageException exception) {
                    exception.printStackTrace();
                    SQSUtil.status(document.get("id").toString(), key, "FAIL", exception.getMessage(), message.getId());
                } catch (TemporaryException | CaptchaException | Exception exception) {
                    exception.printStackTrace();
                    errors.add(document);
                } finally {
                    stopWatch.stop();
                    long seconds = (stopWatch.getTotalTimeMillis() / 1000);
                    if(seconds >= visibilityTimeout) {
                        throw new ExpiredException("Message expired: " + message);
                    }
                    SQSUtil.changeMessageVisibility(queueUrl, receiptHandle, visibilityTimeout);

                    boolean hasBlockedMessage = isBlockWithMessage(driver);
                    if(hasBlockedMessage) {
                        this.logout(driver);
                        this.loginECAC(driver);
                    }
                }
            }

            SQSUtil.resend(queueUrl, message, "documents", errors, messageGroupId);

            acknowledgment.acknowledge();
        } catch (ExpiredException | CaptchaException | Exception exception) {
            exception.printStackTrace();
            throw new Exception(exception);
        } catch (CertificateException exception) {
            exception.printStackTrace();
            //acknowledgment.acknowledge();
        } finally {
            if(Objects.nonNull(driver)) {
                for (String handle : driver.getWindowHandles()) {
                    driver.switchTo().window(handle);
                    driver.close();
                }
            }
            if(Objects.nonNull(driver)) {
                driver.quit();
            }
            if(Objects.nonNull(httpProxyServer)) {
                httpProxyServer.abort();
            }
            if(Objects.nonNull(tmpdir)) {
                FileUtils.cleanDirectory(new File(tmpdir));
            }
        }
    }
}
