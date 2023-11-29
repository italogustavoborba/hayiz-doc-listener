package br.com.wlabs.hayiz.doc.listener.util;

import org.apache.commons.io.IOUtils;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.x509.AccessDescription;
import org.bouncycastle.asn1.x509.AuthorityInformationAccess;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.security.cert.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;

public class CertificateUtil {

    public static KeyStore.PrivateKeyEntry buildCert(InputStream inputStream, String password)
            throws CertificateException, KeyStoreException, UnrecoverableEntryException,
            NoSuchAlgorithmException, IOException {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        load(keyStore, inputStream, password.toCharArray());

        KeyStore.PrivateKeyEntry keyEntry = null;
        Enumeration<String> aliasesEnum = keyStore.aliases();
        while (aliasesEnum.hasMoreElements()) {
            String alias = aliasesEnum.nextElement();
            if (keyStore.isKeyEntry(alias)) {
                keyEntry = (KeyStore.PrivateKeyEntry) keyStore.getEntry(alias,
                        new KeyStore.PasswordProtection(password.toCharArray()));
                X509Certificate x509Certificate = ((X509Certificate) keyStore.getCertificate(alias));
                x509Certificate.checkValidity();
                break;
            }
        }
        return keyEntry;
    }

    public static void load(KeyStore keystore, InputStream is, char[] storePass)
            throws NoSuchAlgorithmException, IOException, NoSuchAlgorithmException, CertificateException {
        if (is == null) {
            keystore.load(null, storePass);
        } else {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int numRead;
            while ((numRead = is.read(buf)) >= 0) {
                baos.write(buf, 0, numRead);
            }
            baos.close();
            // Don't close is. That remains the callers responsibilty.

            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());

            keystore.load(bais, storePass);
        }
    }

    public static List<String> getCaIssuers(X509Certificate cert)
            throws IOException {
        List<String> caIssuers = new ArrayList<>();
        ASN1Primitive aiaDer = JcaX509ExtensionUtils.parseExtensionValue(
                cert.getExtensionValue(Extension.authorityInfoAccess.getId()));
        AuthorityInformationAccess aia = AuthorityInformationAccess
                .getInstance(aiaDer);
        for (AccessDescription desc : aia.getAccessDescriptions()) {
            if (desc.getAccessMethod()
                    .equals(AccessDescription.id_ad_caIssuers)) {
                GeneralName loc = desc.getAccessLocation();
                if (loc.getTagNo() == GeneralName.uniformResourceIdentifier)
                    caIssuers.add(loc.getName().toString());
            }
        }
        return caIssuers;
    }

    public static List<X509Certificate> readCertificatesFromPKCS7(String uri)
    {
        List<X509Certificate> certList = new ArrayList<>();
        try {
            URLConnection urlConnection = new URL(uri).openConnection();
            byte[] binaryPKCS7Store = IOUtils.toByteArray(urlConnection.getInputStream());
            try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(binaryPKCS7Store)) {
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                Iterator i = cf.generateCertificates( byteArrayInputStream ).iterator();
                while ( i.hasNext() )
                {
                    certList.add((X509Certificate) i.next());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return certList;
    }
}
