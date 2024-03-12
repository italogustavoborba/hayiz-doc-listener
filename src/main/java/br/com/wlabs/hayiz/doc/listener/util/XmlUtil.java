package br.com.wlabs.hayiz.doc.listener.util;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.SAXException;

import javax.xml.bind.*;
import javax.xml.crypto.MarshalException;
import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.keyinfo.X509Data;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;

/**
 *
 * @author italo.teixeira
 */
public class XmlUtil {

    public static String sign(String xml, String tag, KeyStore.PrivateKeyEntry keyEntry)
            throws ParserConfigurationException, SAXException, ClassNotFoundException,
            IOException, InstantiationException, IllegalAccessException,
            NoSuchAlgorithmException, InvalidAlgorithmParameterException,
            MarshalException, XMLSignatureException {

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes()));

        NodeList nodeList = document.getElementsByTagName(tag);

        for (int i = 0; i < nodeList.getLength(); i++) {

            XMLSignatureFactory signatureFactory = XMLSignatureFactory.getInstance("DOM",
                    (Provider) Class.forName(
                            System.getProperty("jsr105Provider", "org.jcp.xml.dsig.internal.dom.XMLDSigRI"))
                            .newInstance());

            ArrayList x509Content = new ArrayList();
            X509Certificate cert = (X509Certificate) keyEntry.getCertificate();
            x509Content.add(cert);

            TransformParameterSpec tps = null;

            ArrayList<Transform> transformList = new ArrayList<>();
            Transform envelopedTransform = signatureFactory.newTransform(Transform.ENVELOPED, tps);
            transformList.add(envelopedTransform);

            Transform c14n = signatureFactory.newTransform("http://www.w3.org/TR/2001/REC-xml-c14n-20010315", tps);
            transformList.add(c14n);

            KeyInfoFactory kif = signatureFactory.getKeyInfoFactory();
            X509Data xd = kif.newX509Data(x509Content);
            KeyInfo ki = kif.newKeyInfo(Collections.singletonList(xd));

            Element element = (Element) nodeList.item(i);

            String id = element.getAttribute("Id");
            element.setIdAttribute("Id", true);

            Reference ref = signatureFactory.newReference("#" + id, signatureFactory.newDigestMethod(DigestMethod.SHA256, null), transformList, null, null);
            SignedInfo si = signatureFactory.newSignedInfo(signatureFactory.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE, (C14NMethodParameterSpec) null), signatureFactory.newSignatureMethod(SignatureMethod.RSA_SHA1, null), Collections.singletonList(ref));

            XMLSignature signature = signatureFactory.newXMLSignature(si, ki);
            DOMSignContext dsc = new DOMSignContext(keyEntry.getPrivateKey(), element.getParentNode());

            signature.sign(dsc);
        }

        DOMImplementationLS domImplementation = (DOMImplementationLS) document.getImplementation();
        LSSerializer lsSerializer = domImplementation.createLSSerializer();

        return lsSerializer.writeToString(document);
    }

    public static Document sign(Document document, String tag, KeyStore.PrivateKeyEntry keyEntry)
            throws ParserConfigurationException, SAXException, ClassNotFoundException,
            IOException, InstantiationException, IllegalAccessException,
            NoSuchAlgorithmException, InvalidAlgorithmParameterException,
            MarshalException, XMLSignatureException {

        NodeList nodeList = document.getElementsByTagName(tag);

        for (int i = 0; i < nodeList.getLength(); i++) {

            XMLSignatureFactory signatureFactory = XMLSignatureFactory.getInstance("DOM",
                    (Provider) Class.forName(
                                    System.getProperty("jsr105Provider", "org.jcp.xml.dsig.internal.dom.XMLDSigRI"))
                            .newInstance());

            ArrayList x509Content = new ArrayList();
            X509Certificate cert = (X509Certificate) keyEntry.getCertificate();
            x509Content.add(cert);

            TransformParameterSpec tps = null;

            ArrayList<Transform> transformList = new ArrayList<>();
            Transform envelopedTransform = signatureFactory.newTransform(Transform.ENVELOPED, tps);
            transformList.add(envelopedTransform);

            Transform c14n = signatureFactory.newTransform("http://www.w3.org/TR/2001/REC-xml-c14n-20010315", tps);
            transformList.add(c14n);

            KeyInfoFactory kif = signatureFactory.getKeyInfoFactory();
            X509Data xd = kif.newX509Data(x509Content);
            KeyInfo ki = kif.newKeyInfo(Collections.singletonList(xd));

            Element element = (Element) nodeList.item(i);

            //String id = element.getAttribute("id");
            //element.setIdAttribute("id", true);
            //Reference ref = signatureFactory.newReference("#" + id, signatureFactory.newDigestMethod(DigestMethod.SHA256, null), transformList, null, null);
            Reference ref = signatureFactory.newReference("", signatureFactory.newDigestMethod(DigestMethod.SHA256, null), transformList, null, null);
            SignedInfo si = signatureFactory.newSignedInfo(signatureFactory.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE, (C14NMethodParameterSpec) null),
                    signatureFactory.newSignatureMethod("http://www.w3.org/2001/04/xmldsig-more#rsa-sha256", null), Collections.singletonList(ref));

            XMLSignature signature = signatureFactory.newXMLSignature(si, ki);
            DOMSignContext dsc = new DOMSignContext(keyEntry.getPrivateKey(), element.getParentNode());

            signature.sign(dsc);
        }

        return document;
    }

    public static Document signReinf(Document document, String tag, KeyStore.PrivateKeyEntry keyEntry)
            throws ParserConfigurationException, SAXException, ClassNotFoundException,
            IOException, InstantiationException, IllegalAccessException,
            NoSuchAlgorithmException, InvalidAlgorithmParameterException,
            MarshalException, XMLSignatureException {

        NodeList nodeList = document.getElementsByTagName(tag);

        for (int i = 0; i < nodeList.getLength(); i++) {

            XMLSignatureFactory signatureFactory = XMLSignatureFactory.getInstance("DOM",
                    (Provider) Class.forName(
                                    System.getProperty("jsr105Provider", "org.jcp.xml.dsig.internal.dom.XMLDSigRI"))
                            .newInstance());

            ArrayList x509Content = new ArrayList();
            X509Certificate cert = (X509Certificate) keyEntry.getCertificate();
            x509Content.add(cert);

            TransformParameterSpec tps = null;

            ArrayList<Transform> transformList = new ArrayList<>();
            Transform envelopedTransform = signatureFactory.newTransform(Transform.ENVELOPED, tps);
            transformList.add(envelopedTransform);

            Transform c14n = signatureFactory.newTransform("http://www.w3.org/TR/2001/REC-xml-c14n-20010315", tps);
            transformList.add(c14n);

            KeyInfoFactory kif = signatureFactory.getKeyInfoFactory();
            X509Data xd = kif.newX509Data(x509Content);
            KeyInfo ki = kif.newKeyInfo(Collections.singletonList(xd));

            Element element = (Element) nodeList.item(i);

            String id = element.getAttribute("id");
            element.setIdAttribute("id", true);
            Reference ref = signatureFactory.newReference("#" + id, signatureFactory.newDigestMethod(DigestMethod.SHA256, null), transformList, null, null);

            SignedInfo si = signatureFactory.newSignedInfo(signatureFactory.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE, (C14NMethodParameterSpec) null),
                    signatureFactory.newSignatureMethod("http://www.w3.org/2001/04/xmldsig-more#rsa-sha256", null), Collections.singletonList(ref));

            XMLSignature signature = signatureFactory.newXMLSignature(si, ki);
            DOMSignContext dsc = new DOMSignContext(keyEntry.getPrivateKey(), element.getParentNode());

            signature.sign(dsc);
        }

        return document;
    }

    public static <T> T xmlToObject(String xml, Class<T> classe) throws JAXBException {

        JAXBContext context = JAXBContext.newInstance(classe);
        Unmarshaller unmarshaller = context.createUnmarshaller();

        return unmarshaller.unmarshal(new StreamSource(new StringReader(xml)), classe).getValue();
    }

    public static String objectToXml(JAXBElement obj) throws JAXBException {

        StringWriter s = new StringWriter();
        try {
            JAXBContext jc = JAXBContext.newInstance(obj.getValue().getClass());
            Marshaller marshaller = jc.createMarshaller();
            marshaller.setProperty("jaxb.encoding", "Unicode");
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.FALSE);
            marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
            marshaller.marshal(obj, s);
        } catch (JAXBException ex) {
            throw new JAXBException(ex.toString());
        }
        return s.toString();
    }
}
