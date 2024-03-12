package br.com.wlabs.hayiz.doc.listener.test.integracontador;

import br.com.wlabs.hayiz.doc.listener.provider.integracontador.model.*;
import br.com.wlabs.hayiz.doc.listener.util.CertificateUtil;
import br.com.wlabs.hayiz.doc.listener.util.HTTPUtil;
import br.com.wlabs.hayiz.doc.listener.util.XmlUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import okhttp3.*;
import okhttp3.logging.HttpLoggingInterceptor;
import okhttp3.tls.HandshakeCertificates;
import okhttp3.tls.HeldCertificate;
import okio.Buffer;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.w3c.dom.Document;

import javax.xml.bind.JAXB;
import javax.xml.bind.JAXBElement;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

public class Test {

    private static final ObjectMapper mapper = new ObjectMapper();;

    public static void main(String[] args) throws Exception {

        String cnpj = "30670552000178";

        KeyStore.PrivateKeyEntry keyEntry = buildKeyEntry
                ("/Users/italoteixeira/Downloads/LEX CONTABILIS CONTABILIDADE LTDA14664383000107.pfx",
                        "1234");
        HandshakeCertificates handshakeCertificates = HTTPUtil.buildSSL(keyEntry);

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        Set<Cookie> allCookies = Collections.synchronizedSet(new HashSet<>());
        OkHttpClient client =  new OkHttpClient().newBuilder()
                .readTimeout(60, TimeUnit.SECONDS)
                .connectTimeout(60 / 2, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .cache(null)
                .sslSocketFactory(handshakeCertificates.sslSocketFactory(), handshakeCertificates.trustManager())
                .cookieJar(cookieJar((allCookies)))
                .build();

        MediaType mediaType = MediaType.parse("application/x-www-form-urlencoded");
        RequestBody body = RequestBody.create(mediaType, "grant_type=client_credentials");

        Request request = new Request.Builder()
                .url("https://autenticacao.sapi.serpro.gov.br/authenticate")
                .method("POST", body)
                .addHeader("Authorization", "Basic cHJOZkdXWVM3UzNwSkV4X2pUN2pRWm1DcUg4YTptQXhpWWNmS3BTdEFqTVllSTdXR1Q1VWY1a2dh")
                .addHeader("Role-Type", "TERCEIROS")
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .build();
        Response response = client.newCall(request).execute();
        mediaType = response.body().contentType();
        String responseBody = HTTPUtil.bodyToString(response, mediaType.charset());
        HashMap<String, Object> authorizationMap = new Gson().fromJson(responseBody, HashMap.class);

        Dados dados = new Dados();

        Sistema sistema = new Sistema();
        sistema.setId("API Integra Contador");
        dados.setSistema(sistema);

        Termo termo = new Termo();
        termo.setTexto("Autorizo a empresa CONTRATANTE, identificada neste termo de autorização como DESTINATÁRIO, a " +
                "executar as requisições dos serviços web disponibilizados pela API INTEGRA CONTADOR, onde terei o papel " +
                "de AUTOR PEDIDO DE DADOS no corpo da mensagem enviada na requisição do serviço web. Esse termo de " +
                "autorização está assinado digitalmente com o certificado digital do PROCURADOR ou OUTORGADO DO " +
                "CONTRIBUINTE responsável, identificado como AUTOR DO PEDIDO DE DADOS.");
        dados.setTermo(termo);

        AvisoLegal avisoLegal = new AvisoLegal();
        avisoLegal.setTexto("O acesso a estas informações foi autorizado pelo próprio PROCURADOR ou OUTORGADO DO " +
                "CONTRIBUINTE, responsável pela informação, via assinatura digital. É dever do destinatário da " +
                "autorização e consumidor deste acesso observar a adoção de base legal para o tratamento dos dados " +
                "recebidos conforme artigos 7º ou 11º da LGPD (Lei n.º 13.709, de 14 de agosto de 2018), aos direitos " +
                "do titular dos dados (art. 9º, 17 e 18, da LGPD) e aos princípios que norteiam todos os tratamentos de " +
                "dados no Brasil (art. 6º, da LGPD).");
        dados.setAvisoLegal(avisoLegal);

        Finalidade finalidade = new Finalidade();
        finalidade.setTexto("A finalidade única e exclusiva desse TERMO DE AUTORIZAÇÃO, é garantir que o CONTRATANTE " +
                "apresente a API INTEGRA CONTADOR esse consentimento do PROCURADOR ou OUTORGADO DO CONTRIBUINTE assinado " +
                "digitalmente, para que possa realizar as requisições dos serviços web da API INTEGRA CONTADOR em nome " +
                "do AUTOR PEDIDO DE DADOS (PROCURADOR ou OUTORGADO DO CONTRIBUINTE).");
        dados.setFinalidade(finalidade);

        LocalDate localDate = LocalDate.now(ZoneId.of("America/Recife"));

        DataAssinatura dataAssinatura = new DataAssinatura();
        dataAssinatura.setData(localDate.format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        dados.setDataAssinatura(dataAssinatura);

        Vigencia vigencia = new Vigencia();
        vigencia.setData(localDate.plusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        dados.setVigencia(vigencia);

        Destinatario destinatario = new Destinatario();
        destinatario.setNome("Lex Contabilis Contabilidade LTDA");
        destinatario.setNumero("14664383000107");
        destinatario.setPapel("contratante");
        destinatario.setTipo("PJ");
        dados.setDestinatario(destinatario);

        AssinadoPor assinadoPor = new AssinadoPor();
        assinadoPor.setNome("ZALDO VENANCIO TABOSA:03567242474");
        assinadoPor.setNumero("03567242474");
        assinadoPor.setPapel("autor pedido de dados");
        assinadoPor.setTipo("PF");
        dados.setAssinadoPor(assinadoPor);

        TermoDeAutorizacao termoDeAutorizacao = new TermoDeAutorizacao();
        termoDeAutorizacao.setDados(dados);

        QName qName = new QName("", "termoDeAutorizacao");
        JAXBElement<TermoDeAutorizacao> jaxbElement = new JAXBElement<>(qName, TermoDeAutorizacao.class, null, termoDeAutorizacao);

        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        JAXB.marshal(jaxbElement, new DOMResult(document));

        KeyStore.PrivateKeyEntry keyEntry2 = buildKeyEntry
                ("/Users/italoteixeira/Downloads/ZALDO VENANCIO TABOSA03567242474 (1).pfx",
                        "1234");
        document = XmlUtil.sign(document, "dados", keyEntry2);
        String xml = toString(document);

        HashMap<String, Object> requisition = new HashMap<>();

        HashMap<String, Object> contratante = new HashMap<>();
        contratante.put("numero", "14664383000107");
        contratante.put("tipo", 2);
        requisition.put("contratante", contratante);

        HashMap<String, Object> autorPedidoDados = new HashMap<>();
        autorPedidoDados.put("numero", "03567242474");
        autorPedidoDados.put("tipo", 1);
        requisition.put("autorPedidoDados", autorPedidoDados);

        HashMap<String, Object> contribuinte = new HashMap<>();
        contribuinte.put("numero", cnpj);
        contribuinte.put("tipo", 2);
        requisition.put("contribuinte", contribuinte);

        HashMap<String, Object> pedidoDados = new HashMap<>();
        pedidoDados.put("idSistema", "AUTENTICAPROCURADOR");
        pedidoDados.put("idServico", "ENVIOXMLASSINADO81");
        pedidoDados.put("versaoSistema", "2.0");
        pedidoDados.put("dados", "{\"xml\": \"" + Base64.encodeBase64String(xml.getBytes()) + "\"}");
        requisition.put("pedidoDados", pedidoDados);

        mediaType = MediaType.parse("application/json");

        RequestBody body2 = RequestBody.create(new Gson().toJson(requisition), mediaType);
        request = new Request.Builder()
                .url("https://gateway.apiserpro.serpro.gov.br/integra-contador/v1/Apoiar")
                .method("POST", body2)
                .addHeader("Authorization", "Bearer " + authorizationMap.get("access_token").toString())
                .addHeader("jwt_token", authorizationMap.get("jwt_token").toString())
                .build();
        response = client.newCall(request).execute();
        String autenticarProcuradorToken = tmp("autenticar_procurador_token", response);

        pedidoDados = new HashMap<>();
        pedidoDados.put("idSistema", "SITFIS");
        pedidoDados.put("idServico", "SOLICITARPROTOCOLO91");
        pedidoDados.put("versaoSistema", "2.0");
        pedidoDados.put("dados", "");
        requisition.put("pedidoDados", pedidoDados);

        body2 = RequestBody.create(new Gson().toJson(requisition), mediaType);
        request = new Request.Builder()
                .url("https://gateway.apiserpro.serpro.gov.br/integra-contador/v1/Apoiar")
                .method("POST", body2)
                .headers(response.headers())
                .addHeader("Authorization", "Bearer " + authorizationMap.get("access_token").toString())
                .addHeader("jwt_token", authorizationMap.get("jwt_token").toString())
                .addHeader("autenticar_procurador_token", autenticarProcuradorToken)
                .build();
        response = client.newCall(request).execute();
        String protocoloRelatorio = tmp("protocoloRelatorio", response);
        //String tempoEspera = tmp("tempoEspera", response);

        Thread.sleep(4000);

        pedidoDados.put("idServico", "RELATORIOSITFIS92");
        pedidoDados.put("dados", "{ \"protocoloRelatorio\": \"" + protocoloRelatorio + "\" }");
        requisition.put("pedidoDados", pedidoDados);

        body2 = RequestBody.create(new Gson().toJson(requisition), mediaType);
        request = new Request.Builder()
                .url("https://gateway.apiserpro.serpro.gov.br/integra-contador/v1/Emitir")
                .method("POST", body2)
                .addHeader("Authorization", "Bearer " + authorizationMap.get("access_token").toString())
                .addHeader("jwt_token", authorizationMap.get("jwt_token").toString())
                .addHeader("autenticar_procurador_token", autenticarProcuradorToken)
                .build();
        response = client.newCall(request).execute();

        String pdf = tmp("pdf", response);
        FileUtils.writeByteArrayToFile(new File(cnpj + ".pdf"), Base64.decodeBase64(pdf));
    }

    protected static KeyStore.PrivateKeyEntry buildKeyEntry(String certificatePath, String certificatePassword)
            throws CertificateException {
        try {
            InputStream inputStream = FileUtils.openInputStream(new File(certificatePath));
            KeyStore.PrivateKeyEntry keyEntry = CertificateUtil.buildCert(inputStream, certificatePassword);
            return keyEntry;
        } catch (Exception exception) {
            throw new CertificateException(exception.getMessage(), exception);
        }
    }

    public static String toString(Document doc) {
        try {
            StringWriter sw = new StringWriter();
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            //transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            //transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

            transformer.transform(new DOMSource(doc), new StreamResult(sw));
            return sw.toString();
        } catch (Exception ex) {
            throw new RuntimeException("Error converting to String", ex);
        }
    }

    public static HandshakeCertificates buildSSL(KeyStore.PrivateKeyEntry keyEntry) throws IOException {

        List<String> caIssuers = CertificateUtil.getCaIssuers((X509Certificate) keyEntry.getCertificate());
        List<X509Certificate> x509Certificates = caIssuers.stream()
                .map(CertificateUtil::readCertificatesFromPKCS7)
                .flatMap(Collection::stream)
                .collect(toList());
        X509Certificate[] certificates = x509Certificates.toArray(new X509Certificate[x509Certificates.size()]);

        HandshakeCertificates.Builder builder = new HandshakeCertificates.Builder();
        for (X509Certificate cert : certificates) {
            builder.addTrustedCertificate(cert);
        }

        X509Certificate certificate = (X509Certificate) keyEntry.getCertificate();
        builder.addTrustedCertificate(certificate);
        builder.addPlatformTrustedCertificates();
        builder.heldCertificate(
                new HeldCertificate(new KeyPair(certificate.getPublicKey(), keyEntry.getPrivateKey()), certificate),
                certificates
        );

        return builder.build();
    }

    protected static CookieJar cookieJar(Collection<Cookie> allCookies) {
        return new CookieJar() {
            @Override
            public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                List<String> names = cookies.stream().map(Cookie::name)
                        .collect(toList());
                allCookies.removeIf(cookie -> names.contains(cookie.name()));
                allCookies.addAll(cookies);
            }

            @Override
            public List<Cookie> loadForRequest(HttpUrl url) {
                return allCookies
                        .stream()
                        .collect(toList());
            }
        };
    }

    private static String bodyToString(final Request request){
        try {
            final Request copy = request.newBuilder().build();
            final Buffer buffer = new Buffer();
            copy.body().writeTo(buffer);
            return buffer.readUtf8();
        } catch (final IOException e) {
            return "did not work";
        }
    }

    private static HashMap<String, Object> cacheEtag(final Headers headers) throws IOException {
        HashMap<String, Object> hashMap = new HashMap();
        headers.toMultimap().forEach((key, values) -> {
            if(Objects.equals(key, "etag") && !values.isEmpty()) {
                String value = values.stream().collect(Collectors.joining(" "));
                value = value.substring(1, value.length() - 1);
                hashMap.put(value.split(":")[0], value.split(":")[1]);
            }
        });
        return hashMap;
    }

    private static String tmp(String key, final Response response) throws IOException {
        HashMap<String, Object> hashMap = new HashMap();
        try {
            response.headers().toMultimap().forEach((k, values) -> {
                if(Objects.equals(k, "etag") && !values.isEmpty()) {
                    String value = values.stream().collect(Collectors.joining(" "));
                    value = value.substring(1, value.length() - 1);
                    hashMap.put(value.split(":")[0], value.split(":")[1]);
                }
            });
        } catch (Exception exception) {
            exception.printStackTrace();
        }

        String responseBody = HTTPUtil.bodyToString(response, response.body().contentType().charset());
        HashMap fromJson = (new Gson().fromJson(responseBody, HashMap.class));
        if(Objects.nonNull(fromJson)) {
            hashMap.putAll(fromJson);
        }

        if(hashMap.containsKey("dados")) {
            String dados = (String) hashMap.get("dados");
            if(Objects.nonNull(dados) && !dados.isEmpty()) {
                HashMap<String, Object> map = mapper.readValue(dados, HashMap.class);
                hashMap.putAll(map);
            }
        }

        return (String) hashMap.get(key);
    }

    private static HashMap<String, Object> responseToMap(final Response response) throws IOException {
        HashMap<String, Object> hashMap = new HashMap();
        response.headers().toMultimap().forEach((s, strings) -> {
            if(strings.size() > 0) {
                hashMap.put(s, strings.get(strings.size() - 1));
            }
        });
        String responseBody = HTTPUtil.bodyToString(response, response.body().contentType().charset());
        HashMap tmp = (new Gson().fromJson(responseBody, HashMap.class));
        if(Objects.nonNull(tmp)) {
            hashMap.putAll(tmp);
        }
        if(hashMap.containsKey("etag")) {
            String etag = hashMap.get("etag").toString();
            etag = etag.substring(1, etag.length() - 1);
            hashMap.put(etag.split(":")[0], etag.split(":")[1]);
            hashMap.remove("etag");
        }
        return hashMap;
    }
}
