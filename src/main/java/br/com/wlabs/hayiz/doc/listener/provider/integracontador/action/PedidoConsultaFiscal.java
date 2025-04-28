package br.com.wlabs.hayiz.doc.listener.provider.integracontador.action;

import br.com.wlabs.hayiz.doc.listener.exception.*;
import br.com.wlabs.hayiz.doc.listener.listener.SQSAction;
import br.com.wlabs.hayiz.doc.listener.model.Message;
import br.com.wlabs.hayiz.doc.listener.provider.integracontador.IntegraContadorProvider;
import br.com.wlabs.hayiz.doc.listener.provider.integracontador.model.RequisitionType;
import br.com.wlabs.hayiz.doc.listener.provider.receitafederal.ReceitaFederalProvider;
import br.com.wlabs.hayiz.doc.listener.util.CertificateUtil;
import br.com.wlabs.hayiz.doc.listener.util.SQSUtil;
import br.com.wlabs.hayiz.doc.listener.util.StorageUtil;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.itextpdf.kernel.pdf.canvas.parser.listener.SimpleTextExtractionStrategy;
import okhttp3.Cookie;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.aws.messaging.listener.Acknowledgment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StopWatch;

import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PedidoConsultaFiscal extends IntegraContadorProvider implements SQSAction {

    private Logger log = LoggerFactory.getLogger(PedidoConsultaFiscal.class);

    @Override
    public void process(Message message, String queueUrl, String receiptHandle, String messageGroupId,
                        Acknowledgment acknowledgment) throws Exception {
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
                    if(Objects.nonNull(documents)) {
                        for (Map<String, Object> document : documents) {
                            SQSUtil.status(document.get("id").toString(), null, "FAIL",
                                    "Não foi possível obter os dados: Sistema da Receita Federal indisponível.",
                                    message.getId());
                        }
                    }
                    acknowledgment.acknowledge();
                    return;
                }
            }

            int visibilityTimeout = 300;
            SQSUtil.changeMessageVisibility(queueUrl, receiptHandle, visibilityTimeout);

            ClassPathResource classPathResource =
                    new ClassPathResource("cetificate/1007347755.pfx");
            KeyStore.PrivateKeyEntry keyEntrySSL = CertificateUtil.buildCert(classPathResource.getInputStream(), "1234");

            Set<Cookie> allCookies = Collections.synchronizedSet(new HashSet<>());
            OkHttpClient client = buildClient(allCookies, keyEntrySSL);

            HashMap<String, String> loginData = this.login(client);

            KeyStore.PrivateKeyEntry keyEntry = this.buildKeyEntry((String) data.get("certificateKey"),
                    (String) data.get("certificatePassword"));
            X509Certificate x509Certificate = ((X509Certificate) keyEntry.getCertificate());

            LdapName ldapName = new LdapName(x509Certificate.getSubjectDN().getName());
            String certCN = ldapName.getRdns().stream()
                    .filter(rdn -> rdn.getType().equalsIgnoreCase("CN"))
                    .findFirst()
                    .map(Rdn::getValue)
                    .map(String::valueOf)
                    .orElse(null);
            String[] certNameArr = certCN.split(":");
            if(certNameArr.length < 2) throw new CertificateException();

            String certCode = certNameArr[1];
            String certName = certCN;

            String xmlSigned = this.xmlSigned(keyEntry, certCode, certName);
            String encodedXmlSigned =
                    java.util.Base64.getEncoder().encodeToString(xmlSigned.getBytes(StandardCharsets.UTF_8));

            List<Map<String, Object>> errors = new ArrayList<>();
            List<Map<String, Object>> documents = (List<Map<String, Object>>) data.get("documents");
            for(Map<String, Object> document: documents) {
                StopWatch stopWatch = new StopWatch();
                try {
                    stopWatch.start();

                    String companyCode = document.get("companyCode").toString().replaceAll("[^0-9]", "");
                    String key = data.get("workspaceCode").toString() + "/document/" +
                        document.get("companyCode").toString().replaceAll("[^0-9]", "") + "/" +
                        LocalDate.now().format(DateTimeFormatter.ofPattern("yyMM")) + "/situacao-fiscal-contribuinte-" +
                        UUID.randomUUID() + ".pdf";

                    String _data = "{\"xml\": \"" + encodedXmlSigned + "\"}";
                    Response response = this.buildRequisition(RequisitionType.Apoiar, client, loginData.get("access_token"),
                            loginData.get("jwt_token"), certCode, companyCode, _data, "",
                            "AUTENTICAPROCURADOR","ENVIOXMLASSINADO81", "2.0");
                    String autenticarProcuradorToken = (String) processResponse("autenticar_procurador_token", response);
                    if(Objects.isNull(autenticarProcuradorToken) || autenticarProcuradorToken.isEmpty()) {
                        throw new Exception("TODO");
                    }

                    response = this.buildRequisition(RequisitionType.Apoiar, client, loginData.get("access_token"),
                            loginData.get("jwt_token"), certCode, companyCode, "", autenticarProcuradorToken,
                            "SITFIS", "SOLICITARPROTOCOLO91", "2.0");
                    String protocoloRelatorio = (String) processResponse("protocoloRelatorio", response);
                    if(Objects.isNull(protocoloRelatorio) || protocoloRelatorio.isEmpty()) {
                        throw new Exception("TODO");
                    }

                    Integer tempoEspera = (Integer) processResponse("tempoEspera", response);
                    tempoEspera = Objects.nonNull(tempoEspera) ? tempoEspera : 10000;
                    Thread.sleep(tempoEspera);

                    _data = "{ \"protocoloRelatorio\": \"" + protocoloRelatorio + "\" }";
                    response = this.buildRequisition(RequisitionType.Emitir, client, loginData.get("access_token"), loginData.get("jwt_token"),
                            certCode, companyCode, _data, autenticarProcuradorToken,
                            "SITFIS","RELATORIOSITFIS92", "2.0");
                    String pdf = (String) processResponse("pdf", response);
                    if(Objects.isNull(pdf) || pdf.isEmpty()) {
                        throw new Exception("TODO");
                    }

                    byte[] pdfArr = Base64.decodeBase64(pdf);
                    StorageUtil.upload(key, pdfArr, "application/pdf");

                    boolean isSuccess = true;
                    ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(pdfArr);
                    PdfReader pdfReader = new PdfReader(byteArrayInputStream);
                    PdfDocument pdfDocument = new PdfDocument(pdfReader);
                    Pattern pattern = Pattern.compile("(.+SIEF.+)|" +
                            "(.+SICOB.+)|" +
                            "(.+ECF.+)|" +
                            "(.+DCTF.+)|" +
                            "(.+SIDA.+)|" +
                            "(.+SISPAR.+)|" +
                            "(.+Sistema DIVIDA.+)|" +
                            "(.+AGUIA.+)|" +
                            "(.+Ausência de Declaração.+)|" +
                            "(.+SIPADE.+)|" +
                            "(.+PARCSN/PARCMEI.+)");
                    int pages = pdfDocument.getNumberOfPages();
                    for (int i = 1; i <= pages; i++) {
                        PdfPage page = pdfDocument.getPage(i);
                        String pageContent = PdfTextExtractor.getTextFromPage(page, new SimpleTextExtractionStrategy());
                        Matcher matcher = pattern.matcher(pageContent);
                        while (matcher.find()) {
                            String content = Normalizer.normalize(matcher.group(), Normalizer.Form.NFD)
                                    .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
                            if((content.toLowerCase().contains("debito") || content.toLowerCase().contains("pendencia")
                                    || content.toLowerCase().contains("omissao"))
                                    && !content.toLowerCase().contains("exigibilidade suspensa")) {
                                isSuccess = false;
                                break;
                            }
                        }
                    }
                    try {
                        pdfDocument.close();
                    } catch (Exception exception) {
                        //exception.printStackTrace();
                    }

                    SQSUtil.status(document.get("id").toString(), key, (isSuccess ? "DONE" : "FOUND_ISSUE"), null, message.getId());

                } catch (MessageException exception) {
                    SQSUtil.status(document.get("id").toString(), "", "FAIL", exception.getMessage(), message.getId());
                } catch (Exception exception) {
                    errors.add(document);
                    exception.printStackTrace();
                } finally {
                    stopWatch.stop();
                    long seconds = (stopWatch.getTotalTimeMillis() / 1000);
                    if(seconds >= visibilityTimeout) {
                        throw new ExpiredException("Message expired: " + message);
                    }
                    SQSUtil.changeMessageVisibility(queueUrl, receiptHandle, visibilityTimeout);
                }
            }

            SQSUtil.resend(queueUrl, message, "documents", errors, messageGroupId);
            acknowledgment.acknowledge();
        } catch (Exception | ExpiredException e) {
            throw new Exception(e);
        } catch (CertificateException exception) {
            exception.printStackTrace();
            acknowledgment.acknowledge();
        }
    }
}
