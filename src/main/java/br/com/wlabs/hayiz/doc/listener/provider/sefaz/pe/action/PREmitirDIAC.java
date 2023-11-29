package br.com.wlabs.hayiz.doc.listener.provider.sefaz.pe.action;

import br.com.wlabs.hayiz.doc.listener.exception.ExpiredException;
import br.com.wlabs.hayiz.doc.listener.listener.SQSAction;
import br.com.wlabs.hayiz.doc.listener.model.Message;
import br.com.wlabs.hayiz.doc.listener.provider.sefaz.pe.SefazPernambucoProvider;
import br.com.wlabs.hayiz.doc.listener.util.HTMLUtil;
import br.com.wlabs.hayiz.doc.listener.util.SQSUtil;
import okhttp3.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.FormElement;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.aws.messaging.listener.Acknowledgment;
import org.springframework.util.StopWatch;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class PREmitirDIAC extends SefazPernambucoProvider implements SQSAction {

    private Logger log = LoggerFactory.getLogger(PREmitirDIAC.class);

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

            if(!data.containsKey("registrations")) {
                acknowledgment.acknowledge();
                log.debug("Registrations is empty: " + message);
                return;
            }

            Set<Cookie> allCookies = Collections.synchronizedSet(new HashSet<>());
            OkHttpClient client = buildClientPublic(allCookies);

            int visibilityTimeout = 300;
            SQSUtil.changeMessageVisibility(queueUrl, receiptHandle, visibilityTimeout);

            List<Map<String, Object>> registrations = (List<Map<String, Object>>) data.get("registrations");
            for(Map<String, Object> registration: registrations) {
                StopWatch stopWatch = new StopWatch();
                try {
                    stopWatch.start();
                    Request request = new Request.Builder()
                            .url("https://efisco.sefaz.pe.gov.br/sfi_trb_gcc/PREmitirDIAC")
                            .method("GET", null)
                            .build();
                    Response response = client.newCall(request).execute();
                    MediaType mediaType = response.body().contentType();
                    Document doc = Jsoup.parse(response.body().byteStream(), mediaType.charset().name(),
                            "https://efisco.sefaz.pe.gov.br/sfi/");

                    Map<String, String> formData = HTMLUtil.formElementToMap((FormElement) doc.selectFirst("form[name=frm_principal]"));
                    formData.put("evento", "processarFiltroConsulta");
                    formData.put("TpDocumentoIdentificacao", "1");
                    formData.put("NuDocumentoIdentificacao", registration.get("registrationCode").toString()
                            .replaceAll("[^0-9]", ""));
                    formData.put("qt_registros_pagina", "1");

                    request = new Request.Builder()
                            .url("https://efisco.sefaz.pe.gov.br/sfi_trb_gcc/PREmitirDIAC")
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
                            if(tds.size() >= 5) {
                                HashMap<String, Object> dataMessage = new HashMap<>();
                                dataMessage.put("id", registration.get("registrationId"));
                                dataMessage.put("code", (String) registration.get("registrationCode"));
                                dataMessage.put("workspaceCode", (String) data.get("workspaceCode"));
                                dataMessage.put("status", tds.get(5).text());

                                HashMap<String, Object> dataHeader = new HashMap<>();
                                dataHeader.put("id", UUID.randomUUID().toString());
                                dataHeader.put("action", "RegistrationStatus");
                                dataHeader.put("date", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
                                dataHeader.put("data", dataMessage);

                                SQSUtil.sendMessage(dataHeader, message.getId());
                            }
                        }
                    }
                } catch (Exception exception) {
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
            acknowledgment.acknowledge();
        } catch (Exception | ExpiredException e) {
            throw new Exception(e);
        }
    }
}
