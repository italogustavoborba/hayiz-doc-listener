package br.com.wlabs.hayiz.doc.listener.provider.receitafederal.action;

import br.com.wlabs.hayiz.doc.listener.exception.*;
import br.com.wlabs.hayiz.doc.listener.listener.SQSAction;
import br.com.wlabs.hayiz.doc.listener.model.Message;
import br.com.wlabs.hayiz.doc.listener.provider.receitafederal.ReceitaFederalProvider;
import br.com.wlabs.hayiz.doc.listener.util.SQSUtil;
import br.com.wlabs.hayiz.doc.listener.util.StorageUtil;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.itextpdf.kernel.pdf.canvas.parser.listener.SimpleTextExtractionStrategy;
import org.apache.commons.io.FileUtils;
import org.littleshoot.proxy.HttpProxyServer;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.chrome.ChromeDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.aws.messaging.listener.Acknowledgment;
import org.springframework.util.StopWatch;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class RelatorioDevedor extends ReceitaFederalProvider implements SQSAction {

    private Logger log = LoggerFactory.getLogger(RelatorioDevedor.class);

    @Override
    public void process(Message message, String queueUrl, String receiptHandle, String messageGroupId,
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

            int visibilityTimeout = 300;
            SQSUtil.changeMessageVisibility(queueUrl, receiptHandle, visibilityTimeout);

            KeyStore.PrivateKeyEntry keyEntry = this.buildKeyEntry((String) data.get("certificateKey"),
                    (String) data.get("certificatePassword"));

            httpProxyServer = buildDriverProxySSL(keyEntry);
            Proxy proxy = new Proxy();
            List<String> noProxies = Arrays.asList("www.receita.fazenda.gov.br", "cav.receita.fazenda.gov.br",
                    "hcaptcha.com", "newassets.hcaptcha.com", "barra.brasil.gov.br", "vlibras.gov.br",
                    "consentimento.acesso.gov.br", "estaleiro.serpro.gov.br", "sic.cav.receita.fazenda.gov.br",
                    "cdn.dsgovserprodesign.estaleiro.serpro.gov.br", "cdnjs.cloudflare.com", "googletagmanager.com",
                    "google-analytics.com");
            proxy.setNoProxy(noProxies.stream().collect(Collectors.joining(",")));
            proxy.setSslProxy(httpProxyServer.getListenAddress().getHostName() + ":" + httpProxyServer.getListenAddress().getPort());

            boolean isBlockWithCaptcha = isBlockWithCaptcha(LocalTime.now(zoneId));

            Path path = Paths.get(FileUtils.getTempDirectory().getAbsolutePath(), UUID.randomUUID().toString());
            tmpdir = Files.createDirectories(path).toFile().getAbsolutePath();

            Map<String, Object> prefs = new HashMap<>();
            driver = buildChromeDriver(tmpdir, prefs, 10, proxy, false);

            this.loginECAC(driver);

            List<Map<String, Object>> errors = new ArrayList<>();
            List<Map<String, Object>> documents = (List<Map<String, Object>>) data.get("documents");
            for(Map<String, Object> document: documents) {
                StopWatch stopWatch = new StopWatch();
                stopWatch.start();

                String key = data.get("workspaceCode").toString() + "/document/" +
                        document.get("companyCode").toString().replaceAll("[^0-9]", "") + "/" +
                        LocalDate.now().format(DateTimeFormatter.ofPattern("yyMM")) + "/relatorio-devedor-" +
                        UUID.randomUUID() + ".pdf";

                try {
                    driver.get("https://cav.receita.fazenda.gov.br/ecac/");

                    this.solveCaptcha(driver);

                    this.perfilECAC(driver, document.get("companyCode").toString().replaceAll("\\D", ""));

                    FileUtils.cleanDirectory(new File(tmpdir + "/download"));

                    driver.get("https://sic.cav.receita.fazenda.gov.br/pre-cadin-internet/api/contribuinterepresentado/relatoriodevedor/pdf?");

                    this.solveCaptcha(driver);

                    Collection<File> files = listFilesWaitFor(new File(tmpdir + "/download"), new String[]{"pdf"}, 20);
                    if(files.isEmpty()) throw new TemporaryException(tmpdir + "/download");

                    byte[] bytes = com.amazonaws.util.IOUtils.toByteArray(new FileInputStream(files.stream().findFirst().get()));

                    ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
                    PdfReader pdfReader = new PdfReader(byteArrayInputStream);
                    PdfDocument pdfDocument = new PdfDocument(pdfReader);
                    Pattern pattern = Pattern.compile("(.*NAO INCLUIDO PELA RFB.*)|(.*EXCLUIDO PELA RFB.*)");
                    boolean foundContent = false;
                    int pages = pdfDocument.getNumberOfPages();
                    for (int i = 1; i <= pages; i++) {
                        PdfPage page = pdfDocument.getPage(i);
                        String pageContent = PdfTextExtractor.getTextFromPage(page, new SimpleTextExtractionStrategy());
                        pageContent = Normalizer.normalize(pageContent, Normalizer.Form.NFD)
                                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
                        Matcher matcher = pattern.matcher(pageContent);
                        foundContent = matcher.find();
                        if(foundContent) break;
                    }
                    try {
                        pdfDocument.close();
                    } catch (Exception exception) {
                        //exception.printStackTrace();
                    }

                    StorageUtil.upload(key, bytes, "application/pdf");
                    SQSUtil.status(document.get("id").toString(), key, (foundContent ? "DONE" : "FOUND_ISSUE"), null, message.getId());

                } catch (MessageException exception) {
                    SQSUtil.status(document.get("id").toString(), key, "FAIL", exception.getMessage(), message.getId());
                } catch (TemporaryException | CaptchaException | Exception exception) {
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
        } catch (ExpiredException | CaptchaException exception) {
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
                FileUtils.deleteDirectory(new File(tmpdir));
            }
        }
    }
}
