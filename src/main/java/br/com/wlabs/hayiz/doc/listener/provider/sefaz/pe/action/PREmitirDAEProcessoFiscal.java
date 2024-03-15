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
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.FormElement;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.aws.messaging.listener.Acknowledgment;
import org.springframework.util.StopWatch;

import java.net.URLDecoder;
import java.security.KeyStore;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class PREmitirDAEProcessoFiscal extends SefazPernambucoProvider implements SQSAction {

    private Logger log = LoggerFactory.getLogger(PREmitirDAEProcessoFiscal.class);

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

            if(!data.containsKey("registrations")) {
                acknowledgment.acknowledge();
                log.debug("Registrations is empty: " + message);
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
            List<Map<String, Object>> registrations = (List<Map<String, Object>>) data.get("registrations");
            for(Map<String, Object> registration: registrations) {
                StopWatch stopWatch = new StopWatch();
                try {
                    stopWatch.start();
                    Request request = new Request.Builder()
                            .url("https://efiscoi.sefaz.pe.gov.br/sfi_trb_gpf/PREmitirDAEProcessoFiscal")
                            .method("POST", HTMLUtil.mapToFormBody(loginFormData).build())
                            .addHeader("Content-Type", "application/x-www-form-urlencoded")
                            .build();
                    Response response = client.newCall(request).execute();
                    MediaType mediaType = response.body().contentType();
                    Document doc = Jsoup.parse(response.body().byteStream(), mediaType.charset().name(),
                            "https://efisco.sefaz.pe.gov.br/sfi/");

                    Map<String, String> formData = HTMLUtil.formElementToMap((FormElement) doc.selectFirst("form[name=frm_principal]"));
                    formData.put("evento", "processarFiltroConsulta");
                    formData.put("CdAutuadoTipoDocumentoIdentificacao", "1");
                    formData.put("NuDocumentoAutuadoPessoaDocumento", registration.get("registrationCode").toString()
                            .replaceAll("[^0-9]", ""));
                    formData.put("qt_registros_pagina", "999999");

                    request = new Request.Builder()
                            .url("https://efiscoi.sefaz.pe.gov.br/sfi_trb_gpf/PREmitirDAEProcessoFiscal")
                            .method("POST", HTMLUtil.mapToFormBody(formData).build())
                            .addHeader("Content-Type", "application/x-www-form-urlencoded")
                            .build();
                    response = client.newCall(request).execute();
                    mediaType = response.body().contentType();
                    doc = Jsoup.parse(response.body().byteStream(), mediaType.charset().name(),
                            "https://efisco.sefaz.pe.gov.br/sfi/");

                    formData = HTMLUtil.formElementToMap((FormElement) doc.selectFirst("form[name=frm_principal]"));

                    Elements chavesPrimariaElement = doc.select("input[name=chave_primaria]");
                    for (Element el: chavesPrimariaElement) {
                        String chavePrimaria = el.attr("value");
                        String[] chavePrimariaArray = URLDecoder.decode(chavePrimaria, "UTF-8").split("\\[\\[\\*]]");
                        String especie = chavePrimariaArray[3];
                        String tipoIdentificacao = chavePrimariaArray[6];
                        String documentoIdentificacao = chavePrimariaArray[7];

                        if(especie.equalsIgnoreCase("PARCELAMENTO")) continue;

                        formData.put("chave_primaria", chavePrimaria);
                        formData.put("evento", "exibirParcelas");

                        request = new Request.Builder()
                                .url("https://efiscoi.sefaz.pe.gov.br/sfi_trb_gpf/PREmitirDAEProcessoFiscal")
                                .method("POST", HTMLUtil.mapToFormBody(formData).build())
                                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                                .build();
                        response = client.newCall(request).execute();
                        mediaType = response.body().contentType();
                        doc = Jsoup.parse(response.body().byteStream(), mediaType.charset().name(),
                                "https://efisco.sefaz.pe.gov.br/sfi/");
                        formData = HTMLUtil.formElementToMap((FormElement) doc.selectFirst("form[name=frm_principal]"));

                        Element nuProcessoElement = doc.selectFirst("input[id=nuProcesso]");

                        for (Element table : doc.select("table[id=table_conteudodados][class=tabeladados]")) {
                            for (Element row : table.select("tr")) {
                                Element chavePrimariaAux = row.selectFirst("input[name=ChavePrimariaAux]");
                                if(chavePrimariaAux != null &&
                                        chavePrimariaAux.hasAttr("value")) {

                                    formData.put("evento", "emitirDAEParcelas");
                                    formData.put("ChavePrimariaAux", chavePrimariaAux.attr("value"));

                                    request = new Request.Builder()
                                            .url("https://efiscoi.sefaz.pe.gov.br/sfi_trb_gpf/PREmitirDAEProcessoFiscal")
                                            .method("POST", HTMLUtil.mapToFormBody(formData).build())
                                            .addHeader("Content-Type", "application/x-www-form-urlencoded")
                                            .build();
                                    response = client.newCall(request).execute();
                                    mediaType = response.body().contentType();
                                    doc = Jsoup.parse(response.body().byteStream(), mediaType.charset().name(),
                                            "https://efisco.sefaz.pe.gov.br/sfi/");
                                    Map<String, String> formData2 = HTMLUtil.formElementToMap((FormElement) doc.selectFirst("form[name=frm_principal]"));
                                    formData2.put("evento", "exibirDocumento");
                                    formData2.put("parametro_metodo_botao", "in_content_type_app_octetstream");

                                    request = new Request.Builder()
                                            .url("https://efiscoi.sefaz.pe.gov.br/sfi_trb_gae/PRGerarDAE")
                                            .method("POST", HTMLUtil.mapToFormBody(formData2).build())
                                            .addHeader("Content-Type", "application/x-www-form-urlencoded")
                                            .build();
                                    response = client.newCall(request).execute();
                                    mediaType = response.body().contentType();
                                    if(!(mediaType.type().equalsIgnoreCase("application")
                                            && mediaType.subtype().equalsIgnoreCase("octet-stream"))) {
                                        continue;
                                    }

                                    Elements tds = row.select("td");
                                    String nuProcesso = nuProcessoElement.attr("value");

                                    String key = data.get("workspaceCode").toString() + "/invoice/" +
                                            registration.get("companyCode").toString().replaceAll("[^0-9]", "") + "/" +
                                            "registration/" +
                                            registration.get("registrationCode").toString().replaceAll("[^0-9]", "") + "/" +
                                            LocalDate.now().format(DateTimeFormatter.ofPattern("yyMM")) + "/relatorio-dae-10-" +
                                            UUID.randomUUID() + ".pdf";

                                    byte[] bytes = PdfService.signature(response.body().byteStream(), keyEntry);
                                    StorageUtil.upload(key, bytes, "application/pdf");

                                    HashMap<String, Object> dataMessage = new HashMap<>();
                                    dataMessage.put("key", nuProcesso + "/" + tds.get(1).text());
                                    dataMessage.put("uf", "PE");
                                    dataMessage.put("workspaceCode", data.get("workspaceCode"));
                                    dataMessage.put((tipoIdentificacao.equals("1") ? "registrationCode" : "companyCode"), documentoIdentificacao);
                                    dataMessage.put("storageKey", key);
                                    dataMessage.put("type", "DAE");
                                    dataMessage.put("dueDate", LocalDate.parse(tds.get(2).text(), DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                                            .format(DateTimeFormatter.ISO_LOCAL_DATE));
                                    dataMessage.put("price", tds.get(3).text().replaceAll("\\.", "")
                                            .replaceAll("\\,", "\\."));

                                    HashMap<String, Object> dataHeader = new HashMap<>();
                                    dataHeader.put("id", UUID.randomUUID().toString());
                                    dataHeader.put("action", "Invoice");
                                    dataHeader.put("date", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
                                    dataHeader.put("data", dataMessage);

                                    SQSUtil.sendMessage(dataHeader, message.getId());

                                }
                            }
                        }
                    }
                } catch (Exception exception) {
                    errors.add(registration);
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
            SQSUtil.resend(queueUrl, message, "registrations", errors, messageGroupId);

            acknowledgment.acknowledge();
            keepSession.interrupt();
            this.logout(client, loginFormData);
        } catch (CertificateException exception) {
            exception.printStackTrace();
            acknowledgment.acknowledge();
        } catch (Exception | ExpiredException e) {
            e.printStackTrace();
            throw new Exception(e);
        } finally {
            if(Objects.nonNull(keepSession)) {
                keepSession.interrupt();
            }
        }
    }
}
