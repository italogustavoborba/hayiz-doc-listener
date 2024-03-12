package br.com.wlabs.hayiz.doc.listener.provider.integracontador;

import br.com.wlabs.hayiz.doc.listener.exception.CertificateException;
import br.com.wlabs.hayiz.doc.listener.exception.MessageException;
import br.com.wlabs.hayiz.doc.listener.provider.Provider;
import br.com.wlabs.hayiz.doc.listener.provider.integracontador.model.*;
import br.com.wlabs.hayiz.doc.listener.util.HTTPUtil;
import br.com.wlabs.hayiz.doc.listener.util.XmlUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.itextpdf.layout.element.Link;
import okhttp3.*;
import okhttp3.logging.HttpLoggingInterceptor;
import okhttp3.tls.HandshakeCertificates;
import org.apache.commons.codec.binary.Base64;
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
import java.io.IOException;
import java.io.StringWriter;
import java.security.KeyStore;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class IntegraContadorProvider extends Provider {
    private static final ObjectMapper mapper = new ObjectMapper();

    protected HashMap<String, String> login(OkHttpClient client)
            throws Exception, CertificateException {
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
        return new Gson().fromJson(responseBody, HashMap.class);
    }

    protected Response authentication(OkHttpClient client, String accessToken, String jwtToken, String accountantCode,
                                      String accountantName, String companyCode, KeyStore.PrivateKeyEntry keyEntry)
            throws Exception, CertificateException {
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

        String tmp = accountantName.split(":")[1];

        AssinadoPor assinadoPor = new AssinadoPor();
        assinadoPor.setNome(accountantName);
        //assinadoPor.setNumero(accountantCode);
        assinadoPor.setNumero(tmp);
        assinadoPor.setPapel("autor pedido de dados");
        //assinadoPor.setTipo("PF");
        assinadoPor.setTipo(tmp.length() > 11 ? "PJ" : "PF");
        dados.setAssinadoPor(assinadoPor);

        TermoDeAutorizacao termoDeAutorizacao = new TermoDeAutorizacao();
        termoDeAutorizacao.setDados(dados);

        QName qName = new QName("", "termoDeAutorizacao");
        JAXBElement<TermoDeAutorizacao> jaxbElement = new JAXBElement<>(qName, TermoDeAutorizacao.class, null, termoDeAutorizacao);

        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        JAXB.marshal(jaxbElement, new DOMResult(document));
        document = XmlUtil.sign(document, "dados", keyEntry);

        StringWriter sw = new StringWriter();
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");

        transformer.transform(new DOMSource(document), new StreamResult(sw));
        String xmlSign = sw.toString();

        String data = "{\"xml\": \"" + Base64.encodeBase64String(xmlSign.getBytes()) + "\"}";
        return buildRequisition(RequisitionType.Apoiar, client, accessToken, jwtToken, accountantCode, accountantName, companyCode, data, "",
                "AUTENTICAPROCURADOR","ENVIOXMLASSINADO81", "2.0");
    }

    protected Response buildRequisition(RequisitionType requisitionType,
                                        OkHttpClient client, String accessToken, String jwtToken, String accountantCode,
                                        String accountantName,
                                        String companyCode, String data, String autenticarProcuradorToken, String systemName,
                                        String serviceName, String version)
            throws Exception {
        HashMap<String, Object> requisition = new HashMap<>();

        HashMap<String, Object> contratante = new HashMap<>();
        contratante.put("numero", "14664383000107");
        contratante.put("tipo", 2);
        requisition.put("contratante", contratante);

        String tmp = accountantName.split(":")[1];
        HashMap<String, Object> autorPedidoDados = new HashMap<>();
        //autorPedidoDados.put("numero", accountantCode);
        autorPedidoDados.put("numero", tmp);
        autorPedidoDados.put("tipo", (tmp.length() > 11 ? 2 : 1));
        requisition.put("autorPedidoDados", autorPedidoDados);

        HashMap<String, Object> contribuinte = new HashMap<>();
        contribuinte.put("numero", companyCode);
        contribuinte.put("tipo", 2);
        requisition.put("contribuinte", contribuinte);

        HashMap<String, Object> pedidoDados = new HashMap<>();
        pedidoDados.put("idSistema", systemName);
        pedidoDados.put("idServico", serviceName);
        pedidoDados.put("versaoSistema", version);
        pedidoDados.put("dados", data);
        requisition.put("pedidoDados", pedidoDados);

        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(new Gson().toJson(requisition), mediaType);
        Request request = new Request.Builder()
                .url("https://gateway.apiserpro.serpro.gov.br/integra-contador/v1/" + requisitionType.displayName)
                .method("POST", body)
                .addHeader("Authorization", "Bearer " + accessToken)
                .addHeader("jwt_token", jwtToken)
                .addHeader("autenticar_procurador_token", autenticarProcuradorToken)
                .build();
        return client.newCall(request).execute();
    }

    protected static String processResponse(String key, final Response response) throws MessageException, IOException {
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

        if(response.code() == 403) {
            if(hashMap.containsKey("mensagens")) {
                ArrayList mensagens = (ArrayList) hashMap.get("mensagens");
                throw new MessageException(mensagens.toString());
            }
        }

        return (String) hashMap.get(key);
    }

    protected OkHttpClient buildClient(Collection<Cookie> allCookies, KeyStore.PrivateKeyEntry keyEntry) throws Exception {
        HandshakeCertificates handshakeCertificates = HTTPUtil.buildSSL(keyEntry);

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        ExceptionCatchingExecutor executor = new ExceptionCatchingExecutor();

        return new OkHttpClient().newBuilder()
                .readTimeout(60, TimeUnit.SECONDS)
                .connectTimeout(60 / 2, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                //.proxySelector(ProxyUtil.proxySelector())
                .dispatcher(new Dispatcher(executor))
                .cache(null)
                //.addNetworkInterceptor(logging)
                .addInterceptor(this.delayInterceptor(1000L, TimeUnit.MILLISECONDS))
                .addInterceptor(this.defaultInterceptor())
                .addInterceptor(this.retryInterceptor())
                .sslSocketFactory(handshakeCertificates.sslSocketFactory(), handshakeCertificates.trustManager())
                .cookieJar(cookieJar((allCookies)))
                .build();
    }

    @Override
    protected void validateResponse(Response response) throws Exception {

    }
}
