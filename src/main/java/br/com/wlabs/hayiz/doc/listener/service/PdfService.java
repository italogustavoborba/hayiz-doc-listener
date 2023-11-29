package br.com.wlabs.hayiz.doc.listener.service;

import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.forms.fields.PdfSignatureFormField;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.StampingProperties;
import com.itextpdf.kernel.pdf.annot.PdfAnnotation;
import com.itextpdf.signatures.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.core.io.ClassPathResource;

import javax.annotation.PostConstruct;
import java.io.*;
import java.security.KeyStore;
import java.security.Security;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class PdfService {

    private static final BouncyCastleProvider provider;

    static {
        provider = new BouncyCastleProvider();
        Security.addProvider(provider);
    }


    public static InputStream htmlToPdf(InputStream inputStream) throws Exception {
        String tmpdir = System.getProperty("java.io.tmpdir");

        String fileName = RandomStringUtils.randomAlphabetic(65);
        FileUtils.copyInputStreamToFile(inputStream, new File(tmpdir + File.separator + fileName + ".html"));

        try {
            List<File> executables = Arrays.asList("C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
                            "/usr/bin/chromium-browser",
                            "/opt/homebrew/bin/chromium",
                            "/usr/local/bin/chromium")
                    .stream()
                    .map(File::new)
                    .collect(Collectors.toList());
            executables.removeIf(file -> !file.exists());
            if(executables.size() == 0) {
                throw new IOException("Cannot run program: No such file or directory: " + executables);
            }

            ProcessBuilder processBuilder = new ProcessBuilder(executables.stream().findFirst().get().getAbsolutePath(),
                    "--headless",
                    "--print-to-pdf-no-header",
                    "--disable-gpu",
                    "--landscape",
                    "--printBackground",
                    //"--single-process",
                    "--no-sandbox",
                    "--disable-extensions", "--no-default-browser-check",
                    "--disable-gl-drawing-for-tests",
                    //"--user-data-dir=/tmp/user-data-dir",
                    "--print-to-pdf=" + tmpdir + File.separator + fileName + ".pdf", tmpdir + File.separator + fileName + ".html");
            Process process = processBuilder.inheritIO().start();
            File pdfFile = new File(tmpdir + File.separator + fileName + ".pdf");
            boolean waitFor = waitFor(pdfFile, 120);
            process.destroy();
            if(!waitFor && pdfFile.length() == 0) {
                throw new FileNotFoundException(pdfFile.getAbsolutePath());
            }
            return new ByteArrayInputStream(FileUtils.readFileToByteArray(pdfFile));
        } catch (Exception exception) {
            throw new Exception(exception);
        } finally {
            FileUtils.deleteQuietly(new File(tmpdir + File.separator + fileName + ".html"));
            FileUtils.deleteQuietly(new File(tmpdir + File.separator + fileName + ".pdf"));
        }
    }

    private static boolean waitFor(File file, int seconds) throws InterruptedException {
        int loopsNumber = 1;
        while (loopsNumber <= seconds) {
            try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
                return in.available() > 0;
            } catch (Exception e) {
                loopsNumber++;
                TimeUnit.MILLISECONDS.sleep(1000);
            }
        }
        return false;
    }

    private static ByteArrayOutputStream createFormField(InputStream inputStream) throws IOException {
        PdfReader reader = new PdfReader(inputStream);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(outputStream);

        PdfDocument pdfDocument = new PdfDocument(reader, writer);
        PdfSignatureFormField signature = PdfSignatureFormField.createSignature(pdfDocument,
                new Rectangle(0, 10, 200, 30));
        signature.setFieldName("signature");
        signature.getWidgets().get(0).setHighlightMode(PdfAnnotation.HIGHLIGHT_OUTLINE).setFlags(PdfAnnotation.PRINT);
        PdfAcroForm.getAcroForm(pdfDocument, true).addField(signature);
        pdfDocument.close();

        return outputStream;
    }

    public static byte[] signature(InputStream inputStream, KeyStore.PrivateKeyEntry keyEntry) throws IOException {
        try {
            PdfReader reader = new PdfReader(new ByteArrayInputStream(createFormField(inputStream).toByteArray()));
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            PdfSigner signer = new PdfSigner(reader, outputStream, new StampingProperties());

            ClassPathResource classPathResource = new ClassPathResource("images/hayiz_logo.png");

            Rectangle rect = new Rectangle(0, 10, 200, 30);
            PdfSignatureAppearance appearance = signer.getSignatureAppearance();
            appearance
                    .setLocationCaption("")
                    .setReasonCaption("")
                    .setReuseAppearance(false)
                    .setPageRect(rect)
                    .setRenderingMode(PdfSignatureAppearance.RenderingMode.GRAPHIC_AND_DESCRIPTION)
                    .setCertificate(keyEntry.getCertificate())
                    .setSignatureCreator("hayiz")
                    .setSignatureGraphic(ImageDataFactory.createPng(classPathResource.getURL()));
            signer.setFieldName("signature");
            signer.setCertificationLevel(PdfSigner.CERTIFIED_NO_CHANGES_ALLOWED);

            IExternalSignature pks = new PrivateKeySignature(keyEntry.getPrivateKey(), DigestAlgorithms.SHA256, provider.getName());
            IExternalDigest digest = new BouncyCastleDigest();

            signer.signDetached(digest, pks, keyEntry.getCertificateChain(), null, null, null, 0,
                    PdfSigner.CryptoStandard.CMS);

            return outputStream.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return IOUtils.toByteArray(inputStream);
    }
}
