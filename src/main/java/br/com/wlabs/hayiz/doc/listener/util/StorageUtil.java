package br.com.wlabs.hayiz.doc.listener.util;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.util.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public final class StorageUtil {

    private final static AmazonS3 amazonS3Client;
    private final static String bucket;

    static {
        AWSCredentials credentials = new AWSCredentials() {
            @Override
            public String getAWSAccessKeyId() {
                return System.getProperty("aws-access-key", "");
            }

            @Override
            public String getAWSSecretKey() {
                return System.getProperty("aws-secret-key", "");
            }
        };
        AWSCredentialsProvider credentialsProvider = new AWSStaticCredentialsProvider(credentials);
        amazonS3Client = AmazonS3ClientBuilder
                .standard()
                .withCredentials(credentialsProvider)
                .withRegion(System.getProperty("region", "sa-east-1"))
                .build();
        bucket = System.getProperty("bucket", "hayizsis-doc-dev");
    }

    public static void upload(String key, InputStream inputStream, String contentType) throws IOException {
        byte[] bytes = IOUtils.toByteArray(inputStream);
        upload(key, bytes, contentType);
    }

    public static void upload(String key, byte[] bytes, String contentType) throws IOException {
        if(bytes.length == 0) {
            throw new IOException("Empty file " + key);
        }

        ObjectMetadata meta = new ObjectMetadata();
        meta.setContentLength(bytes.length);
        if (Objects.nonNull(contentType)) {
            meta.setContentType(contentType);
        }

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
        PutObjectRequest request = new PutObjectRequest(bucket, key, byteArrayInputStream, meta);
        amazonS3Client.putObject(request);
    }

    public static InputStream getObject(String key) {
        return amazonS3Client.getObject(bucket, key).getObjectContent();
    }
}
