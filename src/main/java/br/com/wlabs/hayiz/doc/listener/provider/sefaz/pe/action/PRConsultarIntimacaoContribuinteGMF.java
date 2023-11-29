package br.com.wlabs.hayiz.doc.listener.provider.sefaz.pe.action;

import br.com.wlabs.hayiz.doc.listener.exception.CertificateException;
import br.com.wlabs.hayiz.doc.listener.exception.ExpiredException;
import br.com.wlabs.hayiz.doc.listener.listener.SQSAction;
import br.com.wlabs.hayiz.doc.listener.model.Message;
import br.com.wlabs.hayiz.doc.listener.provider.sefaz.pe.SefazPernambucoProvider;
import br.com.wlabs.hayiz.doc.listener.service.PdfService;
import br.com.wlabs.hayiz.doc.listener.util.HTMLUtil;
import br.com.wlabs.hayiz.doc.listener.util.SQSUtil;
import br.com.wlabs.hayiz.doc.listener.util.StorageUtil;
import okhttp3.*;
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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class PRConsultarIntimacaoContribuinteGMF extends SefazPernambucoProvider implements SQSAction {

    private Logger log = LoggerFactory.getLogger(PRConsultarIntimacaoContribuinteGMF.class);

    @Override
    public void process(Message message, String queueUrl, final String receiptHandle, String messageGroupId,
                        Acknowledgment acknowledgment) throws Exception {
        Thread keepSession = null;
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

            Set<Cookie> allCookies = Collections.synchronizedSet(new HashSet<>());
            OkHttpClient client = buildClient(allCookies, keyEntry);

            Map<String, String> loginFormData = this.login(client, (String) data.get("certificateCode"));

            keepSession = keepSession(client, loginFormData, 10, TimeUnit.MINUTES);
            keepSession.start();

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
                            document.get("registrationCode").toString().replaceAll("[^0-9]", "") + "/" +
                            LocalDate.now().format(DateTimeFormatter.ofPattern("yyMM")) + "/consultar-extrato-do-malha-fina-" +
                            UUID.randomUUID() + ".pdf";

                    Request request = new Request.Builder()
                            .url("https://efiscoi.sefaz.pe.gov.br/sfi_trb_gmf/PRConsultarIntimacaoContribuinteGMF")
                            .method("POST", HTMLUtil.mapToFormBody(loginFormData).build())
                            .addHeader("Content-Type", "application/x-www-form-urlencoded")
                            .build();
                    Response response = client.newCall(request).execute();
                    MediaType mediaType = response.body().contentType();
                    Document doc = Jsoup.parse(response.body().byteStream(), mediaType.charset().name(),
                            "https://efisco.sefaz.pe.gov.br/sfi/");

                    Map<String, String> formData = HTMLUtil.formElementToMap((FormElement) doc.selectFirst("form[name=frm_principal]"));
                    formData.put("evento", "processarFiltroConsulta");
                    formData.put("CdSituacaoIrregularidade", "0");
                    formData.put("NuInscricaoEstadualContribuinteGMF", document.get("registrationCode").toString()
                            .replaceAll("[^0-9]", ""));
                    formData.put("qt_registros_pagina", "999999");

                    request = new Request.Builder()
                            .url("https://efiscoi.sefaz.pe.gov.br/sfi_trb_gmf/PRConsultarIntimacaoContribuinteGMF")
                            .method("POST", HTMLUtil.mapToFormBody(formData).build())
                            .addHeader("Content-Type", "application/x-www-form-urlencoded")
                            .build();
                    response = client.newCall(request).execute();
                    mediaType = response.body().contentType();

                    doc = Jsoup.parse(response.body().byteStream(), mediaType.charset().name(),
                            "https://efisco.sefaz.pe.gov.br/sfi/");

                    formData = HTMLUtil.formElementToMap((FormElement) doc.selectFirst("form[name=frm_principal]"));

                    Elements chavesPrimariaElement = doc.select("input[name=chave_primaria]");
                    if(chavesPrimariaElement.isEmpty()) {
                        fixCSS(doc);

                        InputStream inputStream = IOUtils.toInputStream(doc.html(), mediaType.charset());
                        InputStream inputStreamPDF = PdfService.htmlToPdf(inputStream);
                        byte[] bytes = PdfService.signature(inputStreamPDF, keyEntry);
                        StorageUtil.upload(key, bytes, "application/pdf");

                        SQSUtil.status(document.get("id").toString(), key, "DONE", null, message.getId());
                        continue;
                    }

                    String status = "UNDEFINED";
                    Element content = null;
                    for (Element el: chavesPrimariaElement) {
                        String chavePrimaria = el.attr("value");
                        formData.put("chave_primaria", chavePrimaria);
                        formData.put("evento", "exibirDetalhamentoConsulta");
                        formData.put("nao_utilizar_id_contexto_sessao", "");

                        request = new Request.Builder()
                                .url("https://efiscoi.sefaz.pe.gov.br/sfi_trb_gmf/PRConsultarIntimacaoContribuinteGMF")
                                .method("POST", HTMLUtil.mapToFormBody(formData).build())
                                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                                .build();
                        response = client.newCall(request).execute();
                        mediaType = response.body().contentType();
                        doc = Jsoup.parse(response.body().byteStream(), mediaType.charset().name(),
                                "https://efisco.sefaz.pe.gov.br/sfi/");

                        for (Element table : doc.select("table[id=table_tabeladados]")) {
                            for (Element row : table.select("tr")) {
                                Elements tds = row.select("td");
                                if(!tds.isEmpty() && tds.size() >= 10) {
                                    Element input = tds.get(11);
                                    if(input != null && input.hasText()) {
                                        if(input.text().trim().indexOf("INTIMADA") != -1) {
                                            status = "FOUND_ISSUE";
                                        }
                                    }
                                }
                            }
                        }

                        if(content == null) {
                            content = doc;
                        } else {
                            content.selectFirst("body")
                                    .appendChild(doc.selectFirst("form"));
                        }
                    }

                    fixCSS(content);

                    InputStream inputStream = IOUtils.toInputStream(content.html(), mediaType.charset());
                    InputStream inputStreamPDF = PdfService.htmlToPdf(inputStream);
                    byte[] bytes = PdfService.signature(inputStreamPDF, keyEntry);
                    StorageUtil.upload(key, bytes, "application/pdf");

                    SQSUtil.status(document.get("id").toString(), key, status, null, message.getId());

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
            keepSession.interrupt();
            this.logout(client, loginFormData);
        } catch (CertificateException exception) {
            exception.printStackTrace();
            acknowledgment.acknowledge();
        } catch (Exception | ExpiredException e) {
            throw new Exception(e);
        } finally {
            if(Objects.nonNull(keepSession)) {
                keepSession.interrupt();
            }
        }
    }
}
