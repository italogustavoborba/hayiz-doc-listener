package br.com.wlabs.hayiz.doc.listener;

import br.com.wlabs.hayiz.doc.listener.exception.CertificateException;
import br.com.wlabs.hayiz.doc.listener.provider.city.pe.garanhuns.GaranhunsProvider;
import br.com.wlabs.hayiz.doc.listener.provider.sefaz.pe.SefazPernambucoProvider;
import br.com.wlabs.hayiz.doc.listener.util.HTMLUtil;
import okhttp3.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.security.KeyStore;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Service
public class Tinus extends GaranhunsProvider {

    //@PostConstruct
    public void test() throws Exception, CertificateException {

        KeyStore.PrivateKeyEntry keyEntry = this.buildKeyEntry(
                "ngLmfcVIzaqMheIrhkDn/certificate/ROBERTO CESAR CORREIA GOMES03779426420.pfx",
                "123456789");

        Set<Cookie> allCookies = Collections.synchronizedSet(new HashSet<>());
        OkHttpClient client = buildClient(allCookies, keyEntry);

        Request request = new Request.Builder()
                .url("https://www.tinus.com.br/csp/GARANHUNS/portal/index.csp" +
                        "?327RJZk0035gAbho07212jaBB2237Bc=getD75kEc608Zvs91252YSBJK197iRyJw2372R2820801HrBa701")
                .method("GET", null)
                .build();
        Response response = client.newCall(request).execute();
        MediaType mediaType = response.body().contentType();
        Document doc = Jsoup.parse(response.body().byteStream(), mediaType.charset().name(),
                "https://www.tinus.com.br");

        login(client);

        request = new Request.Builder()
                .url("https://www.tinus.com.br/csp/GARANHUNS/portal/index.csp" +
                        "?327RJZk0035gAbho07212jaBB2237Bc=getD75kEc608Zvs91252YSBJK197iRyJw2372R2820801HrBa701")
                .method("GET", null)
                .build();
         response = client.newCall(request).execute();
         mediaType = response.body().contentType();
         doc = Jsoup.parse(response.body().byteStream(), mediaType.charset().name(),
                "https://www.tinus.com.br");

        System.out.println("OK");

        //https://www.tinus.com.br/csp/GARANHUNS/portal/index.csp?327RJZk0035gAbho07212jaBB2237Bc=getD75kEc608Zvs91252YSBJK197iRyJw2372R2820801HrBa701

    }
}
