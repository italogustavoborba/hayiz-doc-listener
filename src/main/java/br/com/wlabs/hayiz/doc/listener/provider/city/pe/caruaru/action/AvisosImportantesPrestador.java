package br.com.wlabs.hayiz.doc.listener.provider.city.pe.caruaru.action;

import br.com.wlabs.hayiz.doc.listener.exception.CertificateException;
import br.com.wlabs.hayiz.doc.listener.exception.ExpiredException;
import br.com.wlabs.hayiz.doc.listener.listener.SQSAction;
import br.com.wlabs.hayiz.doc.listener.model.Message;
import br.com.wlabs.hayiz.doc.listener.provider.city.pe.caruaru.CaruaruPernambucoProvider;
import br.com.wlabs.hayiz.doc.listener.service.PdfService;
import br.com.wlabs.hayiz.doc.listener.util.HTTPUtil;
import br.com.wlabs.hayiz.doc.listener.util.SQSUtil;
import br.com.wlabs.hayiz.doc.listener.util.StorageUtil;
import com.google.common.base.Splitter;
import com.google.gson.Gson;
import okhttp3.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.aws.messaging.listener.Acknowledgment;
import org.springframework.util.StopWatch;

import java.nio.charset.Charset;
import java.security.KeyStore;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class AvisosImportantesPrestador extends CaruaruPernambucoProvider implements SQSAction {

    private Logger log = LoggerFactory.getLogger(AvisosImportantesPrestador.class);

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
                    if(Objects.nonNull(documents)) {
                        for (Map<String, Object> document : documents) {
                            SQSUtil.status(document.get("id").toString(), null, "FAIL",
                                    "Não foi possível obter os dados: Sistema indisponível.",
                                    message.getId());
                        }
                    }
                    acknowledgment.acknowledge();
                    return;
                }
            }

            KeyStore.PrivateKeyEntry keyEntry = this.buildKeyEntry((String) data.get("certificateKey"),
                    (String) data.get("certificatePassword"));

            Set<Cookie> allCookies = Collections.synchronizedSet(new HashSet<>());
            OkHttpClient client = buildClient(allCookies);

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
                            LocalDate.now().format(DateTimeFormatter.ofPattern("yyMM")) + "/avisos-importantes-prestador-" +
                            UUID.randomUUID() + ".pdf";

                    String url = "vURL=fisc.giss.com.br/relatorios/rel_2_11_1_aviso_imp.cfm" +
                            "&pid=1019" +
                            "&mobi_num_cadastro=" + document.get("municipalCode").toString().replaceAll("[^0-9]", "") +
                            "&inscrMun=" +
                            "&tipo=prestador" +
                            "&cod_menu=19" +
                            "&org_contador=1" +
                            "&cnpj=" +
                            "&vAtivo=1";

                    Request request = new Request.Builder()
                            .url("https://fisc1.giss.com.br/relatorios/rel_2_11_1_aviso_imp.cfm?" + url)
                            .method("GET", null)
                            .build();
                    Response response = client.newCall(request).execute();
                    MediaType mediaType = response.body().contentType();
                    org.jsoup.nodes.Document doc = Jsoup.parse(response.body().byteStream(), mediaType.charset(Charset.forName("UTF-8")).name(),
                            "https://fisc1.giss.com.br/relatorios/");

                    Element frmRelatorio = doc.selectFirst("form[name=frmRelatorio]");
                    if(Objects.nonNull(frmRelatorio)) {
                        Thread.sleep(5000L);
                        request = new Request.Builder()
                                .url("https://fisc1.giss.com.br/relatorios/arquivos/situacional/rel_2_11_1_history.cfm" +
                                        "?mobi_num_cadastro=" + frmRelatorio.selectFirst("input[name=inscrmun]").attr("value") +
                                        "&inscrmun=" + frmRelatorio.selectFirst("input[name=inscrmun]").attr("value") +
                                        "&cnpj=" + frmRelatorio.selectFirst("input[name=cnpj]").attr("value") +
                                        "&tipo=" +
                                        "&cd_chave=" + frmRelatorio.selectFirst("input[name=cd_chave]").attr("value") +
                                        "&portal=0" +
                                        "&refresh=F")
                                .method("GET", null)
                                .build();
                        response = client.newCall(request).execute();
                        mediaType = response.body().contentType();
                        doc = Jsoup.parse(response.body().byteStream(), mediaType.charset(Charset.forName("UTF-8")).name(),
                                "https://fisc1.giss.com.br/relatorios/");
                    }

                    if(doc.html().toLowerCase().indexOf("este contribuinte n") != -1) {
                        SQSUtil.status(document.get("id").toString(), key, "FAIL", "Este contribuinte não possui obrigação acessória e principal.", message.getId());
                        continue;
                    }

                    Element linkReport = doc.selectFirst("a[target=_blank]");
                    if(Objects.isNull(linkReport) || !linkReport.hasAttr("href")) {
                        throw new Exception("Tente novamente." + document.get("municipalCode"));
                    }

                    String query = linkReport.attr("href").split("\\?")[1];
                    final Map<String, String> paramns = Splitter.on('&').trimResults().withKeyValueSeparator('=').split(query);
                    String chave = paramns.get("cd_chave");
                    String status = "DONE";
                    List<Integer> ids = Arrays.asList(1, 2, 4, 7, 8, 9, 11, 12, 13, 16, 17, 19, 20) ;
                    for (int id: ids) {
                        request = new Request.Builder()
                                .url("https://fisc1.giss.com.br/relatorios/arquivos/situacional/rel_2_11_1_ajax.cfm" +
                                        "?_dc=" + System.currentTimeMillis() +
                                        "&cd_chave=" + chave +
                                        "&tipo=prestador" +
                                        "&inconsistencia=" + id +
                                        "&page=1&start=0&limit=100")
                                .method("GET", null)
                                .addHeader("Connection", "keep-alive")
                                .addHeader("Cache-Control", "max-age=0")
                                .addHeader("Accept", "*/*")
                                .addHeader("Accept-Language", "en-US,en;q=0.9")
                                .build();
                        response = client.newCall(request).execute();
                        mediaType = response.body().contentType();
                        String responseBody = HTTPUtil.bodyToString(response, mediaType.charset());
                        HashMap<String, Object> hashMap = new Gson().fromJson(responseBody, HashMap.class);
                        if(Objects.nonNull(hashMap) && hashMap.size() > 0) {
                            double RecordCount = (Double) hashMap.getOrDefault("RecordCount", 0);
                            if(RecordCount > 0) {
                                status= "FOUND_ISSUE";
                                break;
                            }
                        }
                    }

                    url = "https://fisc1.giss.com.br/relatorios/arquivos/situacional/rel_2_11_1_print.cfm" +
                            "?cd_chave=" + chave + "" +
                            "&mobi_num_cadastro=" + document.get("municipalCode").toString().replaceAll("[^0-9]", "") +
                            "&tipo=prestador";
                    request = new Request.Builder()
                            .url(url)
                            .method("GET", null)
                            .build();
                    response = client.newCall(request).execute();

                    mediaType = response.body().contentType();
                    if(!(mediaType.type().equalsIgnoreCase("application")
                            && mediaType.subtype().equalsIgnoreCase("pdf"))) {
                        throw new Exception("Tente novamente.");
                    }

                    byte[] bytes = PdfService.signature(response.body().byteStream(), keyEntry);
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
        } catch (CertificateException exception) {
            exception.printStackTrace();
            acknowledgment.acknowledge();
        } catch (Exception | ExpiredException e) {
            throw new Exception(e);
        }
    }
}
