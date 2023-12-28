package br.com.wlabs.hayiz.doc.listener.provider;

import br.com.wlabs.hayiz.doc.listener.exception.CaptchaException;
import br.com.wlabs.hayiz.doc.listener.model.Message;
import br.com.wlabs.hayiz.doc.listener.provider.city.pe.toritama.ToritamaPernambucoProvider;
import br.com.wlabs.hayiz.doc.listener.service.PdfService;
import br.com.wlabs.hayiz.doc.listener.util.HTMLUtil;
import br.com.wlabs.hayiz.doc.listener.util.SQSUtil;
import br.com.wlabs.hayiz.doc.listener.util.StorageUtil;
import com.twocaptcha.TwoCaptcha;
import com.twocaptcha.captcha.Captcha;
import com.twocaptcha.captcha.Normal;
import okhttp3.*;
import okhttp3.logging.HttpLoggingInterceptor;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.FormElement;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.MalformedURLException;
import java.security.KeyStore;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class TributosMunicipaisProvider extends Provider {

    private Logger log = LoggerFactory.getLogger(ToritamaPernambucoProvider.class);

    protected enum CONTEXT {
        GRAVATA("gravata"),
        POMBOS("pombos"),
        OROBO("orobo"),
        PESQUEIRA("pesqueira"),
        FEIRA_NOVA("feiranova"),
        ILHA_DE_ITAMARACA("ilhadeitamaraca"),
        BOM_JARDIM("bomjardim"),
        SAO_LOURENCO("saolourenco"),
        PAUDALHO("paudalho"),
        LAGOA_DO_CARRO("lagoadocarro"),
        MORENO("moreno"),
        CATENDE("catende"),
        SAO_JOSE_DA_COROA_GRANDE("saojosedacoroagrande"),
        SAO_CAETANO("saocaetano"),
        AGRESTINA("agrestina"),
        BEZERROS("bezerros"),
        ARCOVERDE("arcoverde"),
        OURICURI("ouricuri");

        public String name;

        CONTEXT(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    protected void extratoDebito(Message message, CONTEXT context, Map<String, Object> document,
                                 KeyStore.PrivateKeyEntry keyEntry) throws CaptchaException, Exception {

        Map<String, Object> data = message.getData();

        String key = data.get("workspaceCode").toString() + "/document/" +
                document.get("companyCode").toString().replaceAll("[^0-9]", "") + "/" +
                document.get("municipalCode").toString().replaceAll("[^0-9]", "") + "/" +
                LocalDate.now().format(DateTimeFormatter.ofPattern("yyMM")) + "/extrato-debito-" +
                UUID.randomUUID() + ".pdf";

        Set<Cookie> allCookies = Collections.synchronizedSet(new HashSet<>());
        OkHttpClient client = buildClient(allCookies);

        Request request = new Request.Builder()
                .url("https://gestor.tributosmunicipais.com.br/redesim/prefeitura/" + context.getName() + "/views/publico/portaldocontribuinte/")
                .method("GET", null)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if(!response.isSuccessful()) throw new Exception("Tente novamente.");
        }

        request = new Request.Builder()
                .url("https://gestor.tributosmunicipais.com.br/redesim/views/publico/portaldocontribuinte/publico/pessoajuridica/pessoajuridica.xhtml")
                .method("GET", null)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if(!response.isSuccessful()) throw new Exception("Tente novamente.");
        }

        request = new Request.Builder()
                .url("https://gestor.tributosmunicipais.com.br/redesim/views/publico/prefWeb/modulos/mercantil/extratoDebitos/extratoDebito.xhtml")
                .method("GET", null)
                .build();
        Document doc;
        try (Response response = client.newCall(request).execute()) {
            if(!response.isSuccessful()) throw new Exception("Tente novamente.");

            MediaType mediaType = response.body().contentType();
            doc = Jsoup.parse(response.body().byteStream(), mediaType.charset().name(),
                    "https://gestor.tributosmunicipais.com.br");
            fixDoc(doc, "https://gestor.tributosmunicipais.com.br");
        }

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
        String base64;
        try (Response response = client.newCall(request).execute()) {
            if(!response.isSuccessful()) throw new Exception("Tente novamente.");

            base64 = Base64.encodeBase64String(response.body().bytes());
        }

        Normal captcha = new Normal();
        captcha.setBase64(base64);
        captcha.setMinLen(5);
        captcha.setMaxLen(5);
        captcha.setLang("pt");

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
        try (Response response = client.newCall(request).execute()) {
            if(!response.isSuccessful()) throw new Exception("Tente novamente.");

            MediaType mediaType = response.body().contentType();
            doc = Jsoup.parse(response.body().byteStream(), mediaType.charset().name(),
                    "https://gestor.tributosmunicipais.com.br");
        }

        Element loginMsg = doc.selectFirst("update[id=loginMsg]");
        if(Objects.nonNull(loginMsg) && loginMsg.hasText()) {
            Document cdata = Jsoup.parse(loginMsg.text(), "https://www.tributosmunicipais.com.br");
            Element messagesWarnDetail = cdata.selectFirst("span[class=ui-messages-warn-detail]");
            if(Objects.nonNull(messagesWarnDetail) ) {
                throw new Exception("Tente novamente: " + messagesWarnDetail.text());
            }

            Element messagesErrorDetail = cdata.selectFirst("span[class=ui-messages-error-detail]");
            if(Objects.nonNull(messagesErrorDetail)) {
                SQSUtil.status(document.get("id").toString(), key, "FAIL", messagesErrorDetail.text(), message.getId());
                return;
            }
        }

        request = new Request.Builder()
                .url("https://gestor.tributosmunicipais.com.br/redesim/views/publico/prefWeb/modulos/mercantil/extratoDebitos/extratoDebito.xhtml")
                .method("GET", null)
                .build();
        InputStream inputStream;
        try (Response response = client.newCall(request).execute()) {
            if(!response.isSuccessful()) throw new Exception("Tente novamente.");

            MediaType mediaType = response.body().contentType();
            doc = Jsoup.parse(response.body().byteStream(), mediaType.charset().name(),
                    "https://gestor.tributosmunicipais.com.br");
            fixDoc(doc, "https://gestor.tributosmunicipais.com.br");
            inputStream = IOUtils.toInputStream(doc.html(), mediaType.charset());
        }

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
                        if(tds.get(4).text().toUpperCase().indexOf("[NF]") != -1) continue;

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
                            try (Response response = client.newCall(request).execute()) {
                                if(!response.isSuccessful()) throw new Exception("Tente novamente.");
                                
                                MediaType mediaType = response.body().contentType();
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
                            }


                        } catch (Exception exception) {
                            exception.printStackTrace();
                        }
                    }
                }
            }
        }

        SQSUtil.status(document.get("id").toString(), key, (hasInvoice ? "FOUND_ISSUE" : "DONE"), null, message.getId());
    }

    protected OkHttpClient buildClient(Collection<Cookie> allCookies) throws Exception {

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        ExceptionCatchingExecutor executor = new ExceptionCatchingExecutor();

        CookieManager cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        JavaNetCookieJar cookieJar = new JavaNetCookieJar(cookieManager);

        return new OkHttpClient().newBuilder()
                .readTimeout(60, TimeUnit.SECONDS)
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .dispatcher(new Dispatcher(executor))
                .cache(null)
                //.addNetworkInterceptor(logging)
                .addInterceptor(this.delayInterceptor(1000L, TimeUnit.MILLISECONDS))
                .addInterceptor(this.defaultInterceptor())
                .addInterceptor(this.retryInterceptor())
                //.cookieJar(cookieJar((allCookies)))
                .cookieJar(cookieJar)
                .build();
    }

    protected void fixDoc(Document doc, String baseUri) throws MalformedURLException {
        Elements stylesheets = doc.root().select("link");
        for(Element stylesheet : stylesheets) {
            String href = stylesheet.attr("href");
            if(Objects.nonNull(href) && !href.isEmpty())
                stylesheet.attr("href", baseUri + href);
        }

        Elements images = doc.root().select("img");
        for(Element image : images) {
            String src = image.attr("src");
            if(Objects.nonNull(src) && !src.isEmpty())
                image.attr("src", baseUri + src);
        }

        Elements scripts = doc.root().select("script");
        for(Element script : scripts) {
            String src = script.attr("src");
            if(Objects.nonNull(src) && !src.isEmpty())
                script.attr("src", baseUri + src);
        }
    }

    protected void solveCaptcha(Captcha captcha) throws CaptchaException {
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
    }

    @Override
    protected void validateResponse(Response response) throws Exception {
        if(Objects.isNull(response) || response.isRedirect()) return;

        MediaType mediaType = response.body().contentType();
        if(mediaType.type().equalsIgnoreCase("application")
                && (mediaType.subtype().equalsIgnoreCase("octet-stream") ||
                mediaType.subtype().equalsIgnoreCase("pdf"))) {
            return;
        }
    }
}
