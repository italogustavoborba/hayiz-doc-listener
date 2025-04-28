package br.com.wlabs.hayiz.doc.listener.provider.city.pe.belojardim.action;

import br.com.wlabs.hayiz.doc.listener.exception.CaptchaException;
import br.com.wlabs.hayiz.doc.listener.exception.CertificateException;
import br.com.wlabs.hayiz.doc.listener.exception.ExpiredException;
import br.com.wlabs.hayiz.doc.listener.listener.SQSAction;
import br.com.wlabs.hayiz.doc.listener.model.Message;
import br.com.wlabs.hayiz.doc.listener.provider.city.pe.belojardim.BeloJardimPernambucoProvider;
import br.com.wlabs.hayiz.doc.listener.service.PdfService;
import br.com.wlabs.hayiz.doc.listener.util.HTMLUtil;
import br.com.wlabs.hayiz.doc.listener.util.SQSUtil;
import br.com.wlabs.hayiz.doc.listener.util.StorageUtil;
import com.twocaptcha.captcha.Normal;
import okhttp3.*;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.FormElement;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.aws.messaging.listener.Acknowledgment;
import org.springframework.util.StopWatch;

import java.io.InputStream;
import java.security.KeyStore;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ExtratoDebitoBeloJardim extends BeloJardimPernambucoProvider implements SQSAction {

    private Logger log = LoggerFactory.getLogger(ExtratoDebitoBeloJardim.class);

    @Override
    public void process(Message message, String queueUrl, final String receiptHandle, String messageGroupId,
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
                    for(Map<String, Object> document: documents) {
                        SQSUtil.status(document.get("id").toString(), null, "FAIL",
                                "Não foi possível obter os dados: Sistema indisponível.",
                                message.getId());
                    }
                    acknowledgment.acknowledge();
                    return;
                }
            }

            KeyStore.PrivateKeyEntry keyEntry = this.buildKeyEntry((String) data.get("certificateKey"),
                    (String) data.get("certificatePassword"));

            int visibilityTimeout = 300;
            SQSUtil.changeMessageVisibility(queueUrl, receiptHandle, visibilityTimeout);

            List<Map<String, Object>> errors = new ArrayList<>();
            List<Map<String, Object>> documents = (List<Map<String, Object>>) data.get("documents");
            for(Map<String, Object> document: documents) {
                StopWatch stopWatch = new StopWatch();
                try {
                    stopWatch.start();

                    String key = data.get("workspaceCode").toString() + "/document/" +
                            document.get("companyCode").toString().replaceAll("[^0-9]", "") + "/" +
                            document.get("municipalCode").toString().replaceAll("[^0-9]", "") + "/" +
                            LocalDate.now().format(DateTimeFormatter.ofPattern("yyMM")) + "/extrato-debito-" +
                            UUID.randomUUID() + ".pdf";

                    Set<Cookie> allCookies = Collections.synchronizedSet(new HashSet<>());
                    OkHttpClient client = buildClient(allCookies);

                    Request request = new Request.Builder()
                            .url("https://gestor.tributosmunicipais.com.br/redesim/prefeitura/belojardim/views/publico/portaldocontribuinte/")
                            .method("GET", null)
                            .build();
                    client.newCall(request).execute();

                    request = new Request.Builder()
                            .url("https://gestor.tributosmunicipais.com.br/redesim/views/publico/portaldocontribuinte/publico/pessoajuridica/pessoajuridica.xhtml")
                            .method("GET", null)
                            .build();
                    client.newCall(request).execute();

                    request = new Request.Builder()
                            .url("https://gestor.tributosmunicipais.com.br/redesim/views/publico/prefWeb/modulos/mercantil/extratoDebitos/extratoDebito.xhtml")
                            .method("GET", null)
                            .build();
                    Response response = client.newCall(request).execute();
                    MediaType mediaType = response.body().contentType();
                    Document doc = Jsoup.parse(response.body().byteStream(), mediaType.charset().name(),
                            "https://gestor.tributosmunicipais.com.br");
                    fixDoc(doc, "https://gestor.tributosmunicipais.com.br");

                    Element painelPrincipalContentElement = doc.selectFirst("div[id=painelPrincipal_content]");
                    if(Objects.isNull(painelPrincipalContentElement)) {
                        throw new Exception("Tente novamente.");
                    }

                    Element form = painelPrincipalContentElement.selectFirst("form");
                    if(Objects.isNull(form)) {
                        throw new Exception("Tente novamente.");
                    }

                    Element imagemCaptcha = doc.selectFirst("img[id=imagemCaptcha]");
                    if(Objects.isNull(imagemCaptcha)) {
                        throw new Exception("Tente novamente.");
                    }

                    request = new Request.Builder()
                            .url(imagemCaptcha.attr("src"))
                            .method("GET", null)
                            .build();
                    response = client.newCall(request).execute();

                    String base64 = Base64.encodeBase64String(response.body().bytes());

                    Normal captcha = new Normal();
                    captcha.setBase64(base64);
                    captcha.setMinLen(5);
                    captcha.setMaxLen(5);
                    //captcha.setLang("pt");

                    this.solveCaptcha(captcha);

                    Map<String, String> formData = HTMLUtil.formElementToMap((FormElement) form);
                    formData.put("cpfCnpj", document.get("companyCode").toString().replaceAll("[^0-9]", ""));
                    formData.put("captcha", captcha.getCode());
                    formData.put("javax.faces.partial.ajax", "true");
                    formData.put("javax.faces.source", "bton2");
                    formData.put("javax.faces.partial.execute", "%40all");
                    formData.put("javax.faces.partial.render", "imagemCaptcha+captchaMsg+cpfCnpjMsg+loginMsg");
                    formData.put("bton2", "bton2");

                    request = new Request.Builder()
                            .url("https://gestor.tributosmunicipais.com.br/redesim/views/publico/prefWeb/modulos/mercantil/extratoDebitos/extratoDebito.xhtml")
                            .method("POST", HTMLUtil.mapToFormBody(formData).build())
                            .addHeader("Content-Type", "application/x-www-form-urlencoded")
                            .build();
                    response = client.newCall(request).execute();
                    doc = Jsoup.parse(response.body().byteStream(), mediaType.charset().name(),
                            "https://gestor.tributosmunicipais.com.br");

                    Element loginMsg = doc.selectFirst("update[id=loginMsg]");
                    if(Objects.nonNull(loginMsg) && loginMsg.hasText()) {
                        Document cdata = Jsoup.parse(loginMsg.text(), "https://www.tributosmunicipais.com.br");
                        Element messagesErrorDetail = cdata.selectFirst("span[class=ui-messages-error-detail]");
                        Element messagesWarnSummary = cdata.selectFirst("span[class=ui-messages-warn-summary]");
                        if(Objects.nonNull(messagesErrorDetail) || Objects.nonNull(messagesWarnSummary)) {
                            SQSUtil.status(document.get("id").toString(), key, "FAIL", messagesErrorDetail.text() + " - " + messagesWarnSummary.text(), message.getId());
                            continue;
                        }
                    }

                    request = new Request.Builder()
                            .url("https://gestor.tributosmunicipais.com.br/redesim/views/publico/prefWeb/modulos/mercantil/extratoDebitos/extratoDebito.xhtml")
                            .method("GET", null)
                            .build();
                    response = client.newCall(request).execute();
                    mediaType = response.body().contentType();
                    doc = Jsoup.parse(response.body().byteStream(), mediaType.charset().name(),
                            "https://gestor.tributosmunicipais.com.br");
                    fixDoc(doc, "https://gestor.tributosmunicipais.com.br");

                    InputStream inputStream = IOUtils.toInputStream(doc.html(), mediaType.charset());
                    InputStream inputStreamPDF = PdfService.htmlToPdf(inputStream);
                    byte[] bytes = PdfService.signature(inputStreamPDF, keyEntry);
                    StorageUtil.upload(key, bytes, "application/pdf");

                    boolean hasInvoice = false;

                    painelPrincipalContentElement = doc.selectFirst("div[id=painelPrincipal_content]");
                    if(Objects.isNull(painelPrincipalContentElement)) {
                        throw new Exception("Tente novamente.");
                    }

                    FormElement tmp = (FormElement) painelPrincipalContentElement.selectFirst("form");
                    formData = HTMLUtil.formElementToMap((FormElement) painelPrincipalContentElement.selectFirst("form"));
                    if(Objects.isNull(formData)) {
                        throw new Exception("Tente novamente.");
                    }

                    Element div = doc.selectFirst("div[class=ui-datatable-scrollable-body]");
                    if(Objects.isNull(div)) {
                        throw new Exception("Tente novamente.");
                    }

                    for (Element table : div.select("table")) {
                        for (Element tbody : table.select("tbody")) {
                            for (Element row : tbody.select("tr")) {

                                if(!row.hasAttr("data-rk")) continue;
                                hasInvoice = true;

                                Elements tds = row.select("td");
                                if (Objects.nonNull(tds)) {
                                    try {
                                        String invoiceKey = data.get("workspaceCode").toString() + "/invoice/" +
                                                document.get("companyCode").toString().replaceAll("[^0-9]", "") + "/" +
                                                "municipal/" +
                                                document.get("municipalCode").toString().replaceAll("[^0-9]", "") + "/" +
                                                LocalDate.now().format(DateTimeFormatter.ofPattern("yyMM")) + "/relatorio-dam-" +
                                                UUID.randomUUID() + ".pdf";

                                        formData.put("tbDebitos_checkbox", "on");
                                        formData.put("tbDebitos_selection", row.attr("data-rk"));
                                        formData.put("botaoMerExtListaDebitosImprimirDam", "");
                                        formData.remove("checkGerarUmBoleto");

                                        request = new Request.Builder()
                                                .url("https://gestor.tributosmunicipais.com.br/redesim/views/publico/prefWeb/modulos/mercantil/extratoDebitos/extratoDebito.xhtml")
                                                .method("POST", HTMLUtil.mapToFormBody(formData).build())
                                                .build();
                                        response = client.newCall(request).execute();
                                        mediaType = response.body().contentType();
                                        if (!(mediaType.type().equalsIgnoreCase("application")
                                                && mediaType.subtype().equalsIgnoreCase("pdf"))) {
                                            doc = Jsoup.parse(response.body().byteStream(), mediaType.charset().name(),
                                                    "https://www.tributosmunicipais.com.br");
                                            Element MsgErrorGlobal2 = doc.selectFirst("span[id=MsgErrorGlobal2]");
                                            if(Objects.nonNull(MsgErrorGlobal2) || MsgErrorGlobal2.hasText()) {
                                                //continue;
                                            }
                                            throw new Exception("Tente novamente.");
                                        }

                                        bytes = PdfService.signature(response.body().byteStream(), keyEntry);
                                        StorageUtil.upload(invoiceKey, bytes, "application/pdf");

                                        HashMap<String, Object> dataMessage = new HashMap<>();
                                        dataMessage.put("key", row.attr("data-rk") + "/" + tds.get(3).text());
                                        dataMessage.put("uf", message.getUf().name());
                                        dataMessage.put("workspaceCode", data.get("workspaceCode"));
                                        dataMessage.put("companyCode", document.get("companyCode").toString().replaceAll("[^0-9]", ""));
                                        dataMessage.put("municipalCode", document.get("municipalCode").toString().replaceAll("[^0-9]", ""));
                                        dataMessage.put("storageKey", invoiceKey);
                                        dataMessage.put("type", "DAM");
                                        dataMessage.put("message", tds.get(2).text());
                                        dataMessage.put("dueDate", LocalDate.parse(tds.get(5).text(), DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                                                .format(DateTimeFormatter.ISO_LOCAL_DATE));
                                        dataMessage.put("price", tds.get(10).text()
                                                .replaceAll("[^0-9\\.\\,]", "")
                                                .replaceAll("\\.", "")
                                                .replaceAll("\\,", "\\."));

                                        HashMap<String, Object> dataHeader = new HashMap<>();
                                        dataHeader.put("id", UUID.randomUUID().toString());
                                        dataHeader.put("action", "Invoice");
                                        dataHeader.put("date", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
                                        dataHeader.put("data", dataMessage);

                                        SQSUtil.sendMessage(dataHeader, message.getId());
                                    } catch (Exception exception) {
                                        exception.printStackTrace();
                                    }
                                }
                            }
                        }
                    }

                    SQSUtil.status(document.get("id").toString(), key, (hasInvoice ? "FOUND_ISSUE" : "DONE"), null, message.getId());

                } catch (CaptchaException | Exception exception) {
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
        } catch (CertificateException exception) {
            exception.printStackTrace();
            acknowledgment.acknowledge();
        } catch (Exception | ExpiredException e) {
            throw new Exception(e);
        }
    }
}
