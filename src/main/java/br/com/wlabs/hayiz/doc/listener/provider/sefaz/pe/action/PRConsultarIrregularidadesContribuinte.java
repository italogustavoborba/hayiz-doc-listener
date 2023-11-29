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
import java.net.URLDecoder;
import java.security.KeyStore;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class PRConsultarIrregularidadesContribuinte extends SefazPernambucoProvider implements SQSAction {

    private Logger log = LoggerFactory.getLogger(PRConsultarIrregularidadesContribuinte.class);

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

            int visibilityTimeout = 600;
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
                            LocalDate.now().format(DateTimeFormatter.ofPattern("yyMM")) + "/detalhamento-de-processo-de-debitos-" +
                            UUID.randomUUID() + ".pdf";

                    Request request = new Request.Builder()
                            .url("https://efiscoi.sefaz.pe.gov.br/sfi_trb_gcc/PRConsultarIrregularidadesContribuinte")
                            .method("POST", HTMLUtil.mapToFormBody(loginFormData).build())
                            .addHeader("Content-Type", "application/x-www-form-urlencoded")
                            .build();
                    Response response = client.newCall(request).execute();
                    MediaType mediaType = response.body().contentType();
                    Document doc = Jsoup.parse(response.body().byteStream(), mediaType.charset().name(),
                            "https://efisco.sefaz.pe.gov.br/sfi/");

                    Map<String, String> formData = HTMLUtil.formElementToMap((FormElement) doc.selectFirst("form[name=frm_principal]"));
                    formData.put("evento", "processarFiltroConsulta");
                    formData.put("TpDocumentoIdentificacaoCertidaoRegularidade", "1");
                    formData.put("NuDocumentoIdentificacaoCertidaoRegularidade", document.get("registrationCode").toString()
                            .replaceAll("[^0-9]", ""));
                    formData.put("qt_registros_pagina", "20");

                    request = new Request.Builder()
                            .url("https://efiscoi.sefaz.pe.gov.br/sfi_trb_gcc/PRConsultarIrregularidadesContribuinte")
                            .method("POST", HTMLUtil.mapToFormBody(formData).build())
                            .addHeader("Content-Type", "application/x-www-form-urlencoded")
                            .build();
                    response = client.newCall(request).execute();
                    mediaType = response.body().contentType();

                    doc = Jsoup.parse(response.body().byteStream(), mediaType.charset().name(),
                            "https://efisco.sefaz.pe.gov.br/sfi/");

                    formData = HTMLUtil.formElementToMap((FormElement) doc.selectFirst("form[name=frm_principal]"));
                    formData.put("TpDocumentoIdentificacaoCertidaoRegularidade", "2");
                    formData.put("NuInscricaoEstadualDEF", document.get("registrationCode").toString().replaceAll("[^0-9]", ""));
                    formData.put("nao_utilizar_id_contexto_sessao", "S");
                    formData.put("qt_registros_pagina", "20");
                    formData.put("cd_menu", "190110");
                    formData.put("areaFavorito", "on");
                    formData.put("in_janela_auxiliar", "S");

                    Elements chavesPrimariaElement = doc.select("input[name=chave_primaria]");
                    if(chavesPrimariaElement.isEmpty()) {
                        fixCSS(doc);

                        InputStream inputStream = IOUtils.toInputStream(doc.html(), mediaType.charset());
                        InputStream inputStreamPDF = PdfService.htmlToPdf(inputStream);
                        byte[] bytes = PdfService.signature(inputStreamPDF, keyEntry);
                        if(bytes.length == 0) {
                            System.out.println("OK");
                        }
                        StorageUtil.upload(key, bytes, "application/pdf");

                        SQSUtil.status(document.get("id").toString(), key, "DONE", null, message.getId());
                        continue;
                    }

                    Element content = null;
                    for (Element el :chavesPrimariaElement) {
                        String chavePrimaria = el.attr("value");
                        formData.put("chave_primaria", chavePrimaria);
                        String[] chavePrimariaArray = URLDecoder.decode(chavePrimaria, "UTF-8").split("\\[\\[\\*]]");
                        formData.put("NuDocumentoIdentificacao", chavePrimariaArray[5]);
                        formData.put("NmRazaoSocialPessoa", chavePrimariaArray[2]);

                        String nuInscricaoEstadual = chavePrimariaArray[1];
                        String nmRazaoSocialPessoa = chavePrimariaArray[2];
                        String cdTipoIndicador = chavePrimariaArray[7];
                        String tpDocumento = chavePrimariaArray[4];
                        String nuDocumento = chavePrimariaArray[5];
                        String url = "/sfi_trb_gcc/PREmitirCertidaoRegularidadeFiscal";
                        formData.put("evento", "exibirDetalhamentoConsulta");

                        switch (cdTipoIndicador) {
                            case "22":
                            case "35":
                                formData.put("evento", "exibirDetalhamentoConsulta");
                                url = "/sfi_trb_gcc/PREmitirCertidaoRegularidadeFiscal";
                                break;
                            case "20":
                            case "21":
                                formData.put("primeiro_campo", nuInscricaoEstadual);
                                formData.put("NuInscricaoEstadualControlePagamento", nuInscricaoEstadual);
                                formData.put("CdNaturezaDaReceita", "000469");
                                formData.put("NmRazaoSocialPessoa", nmRazaoSocialPessoa);
                                formData.put("evento", "processarFiltroConsulta");
                                url = "/sfi_trb_def/PRConsultarICMSAberto?evento=processarFiltroConsulta";
                                break;
                            case "19":
                                formData.put("primeiro_campo", nuInscricaoEstadual);
                                formData.put("NuInscricaoEstadualControlePagamento", nuInscricaoEstadual);
                                formData.put("NmRazaoSocialPessoa", nmRazaoSocialPessoa);
                                formData.put("evento", "processarFiltroConsulta");
                                url = "/sfi_trb_def/PRConsultarICMSAberto?evento=processarFiltroConsulta";
                                break;
                            case "868":
                                formData.put("NuDocumento", nuDocumento);
                                formData.put("TpDocumento", tpDocumento);
                                formData.put("evento", "exibirFiltroConsultaOrigemGCC");
                                url = "/sfi_trb_gcd/PRConsultarProcessoICDIrregular?evento=exibirFiltroConsultaOrigemGCC";
                                break;
                            case "12":
                                formData.put("NuInscricaoEstadualContribuinte", nuInscricaoEstadual);
                                formData.put("evento", "processarFiltroConsulta");
                                url = "/sfi_trb_gsn/PRConsultarDebitosSIM?evento=processarFiltroConsulta";
                                break;
                            case "10":
                                formData.put("NuDocumentoDebitoConsolidado", nuDocumento);
                                formData.put("evento", "processarFiltroConsulta");
                                url = "/sfi_trb_giv/PRConsultarVeiculosComIrregularidade?evento=processarFiltroConsulta";
                                break;
                            case "6":
                                formData.put("NuDocumentoIdentificacao", nuInscricaoEstadual);
                                formData.put("evento", "processarFiltroConsultaIndicadorGCC");
                                url = "/sfi_trb_cmt/PRConsultarExtratoReemissaoDAE?evento=processarFiltroConsultaIndicadorGCC";
                                break;
                            case "97":
                                formData.put("NuInscricaoEstadualDEF", nuInscricaoEstadual);
                                formData.put("evento", "processarFiltroConsulta");
                                url = "/sfi_trb_def/PRConsultarPosicaoDocumentos?evento=processarFiltroConsulta";
                                break;
                            case "5":
                                if (nuInscricaoEstadual != "null" && nuInscricaoEstadual != "") {
                                    formData.put("NuDocumentoIdentificacao", nuInscricaoEstadual);
                                    formData.put("TpDocumentoIdentificacao", "1");
                                } else {
                                    formData.put("NuDocumentoIdentificacao", nuInscricaoEstadual);
                                    formData.put("TpDocumentoIdentificacao", tpDocumento);
                                }
                                formData.put("evento", "processarFiltroConsulta");
                                url = "/sfi_trb_gpf/PRManterProcessoIrregularidade?evento=processarFiltroConsulta";
                                break;
                            case "855":
                                formData.put("NuInscricaoEstadualContribuinteGMF", nuInscricaoEstadual);
                                formData.put("evento", "processarFiltroConsulta");
                                url = "/sfi_trb_gmf/PRConsultarIntimacaoContribuinte?evento=processarFiltroConsulta";
                                break;
                            case "233":
                                formData.put("evento", "processarFiltroConsulta");
                                url = "/sfi_trb_def/PRConsultarPosicaoDocumentos?evento=processarFiltroConsulta";
                                break;
                            case "95":
                                formData.put("evento", "exibirFiltroConsulta");
                                url = "/sfi_trb_gaf/PRConsultarDemandaAcaoFiscal?evento=exibirFiltroConsulta";
                                break;
                        }

                        request = new Request.Builder()
                                .url("https://efiscoi.sefaz.pe.gov.br" + url)
                                .method("POST", HTMLUtil.mapToFormBody(formData).build())
                                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                                .build();
                        response = client.newCall(request).execute();
                        mediaType = response.body().contentType();
                        doc = Jsoup.parse(response.body().byteStream(), mediaType.charset().name(),
                                "https://efisco.sefaz.pe.gov.br/sfi/");

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

                    SQSUtil.status(document.get("id").toString(), key, "UNDEFINED", null, message.getId());

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
