package br.com.wlabs.hayiz.doc.listener.util;

import br.com.wlabs.hayiz.doc.listener.model.Message;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.AmazonSQSAsyncClient;
import com.amazonaws.services.sqs.buffered.AmazonSQSBufferedAsyncClient;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageResult;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SQSUtil {

    private final static AmazonSQSAsync amazonSQSAsync;
    private final static Gson gson;

    private static Logger log = LoggerFactory.getLogger(SQSUtil.class);

    static {
        AWSCredentials credentials = new AWSCredentials() {
            @Override
            public String getAWSAccessKeyId() {
                return System.getProperty("aws-access-key", "AKIARAH5OV3UATBBDYF2");
            }

            @Override
            public String getAWSSecretKey() {
                return System.getProperty("aws-secret-key", "stOPrnHntsdBRWuGX0RzuCOKNHGhV1CNbtsUOllg");
            }
        };
        AWSCredentialsProvider credentialsProvider = new AWSStaticCredentialsProvider(credentials);
        amazonSQSAsync = new AmazonSQSBufferedAsyncClient(AmazonSQSAsyncClient.asyncBuilder()
                .withCredentials(credentialsProvider)
                .withRegion(System.getProperty("region", "sa-east-1"))
                .build());
        gson = new Gson();
    }

    public static void status(String id, String key, String status, String message, String messageGroupId) {
        System.out.println(LocalDateTime.now() + ": status: " + id + " -> " + status + ": " + message);
        HashMap<String, Object> data = new HashMap<>();
        data.put("id", id);
        data.put("key", key);
        data.put("status", status);
        data.put("message", message);

        HashMap<String, Object> invoiceStatus = new HashMap<>();
        invoiceStatus.put("id", UUID.randomUUID().toString());
        invoiceStatus.put("action", "DocumentStatus");
        invoiceStatus.put("date", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
        invoiceStatus.put("data", data);

        String body = gson.toJson(invoiceStatus);
        SendMessageRequest sendMessageRequest = new SendMessageRequest()
                .withQueueUrl("https://sqs.sa-east-1.amazonaws.com/069251018472/hayiz-doc-response-dev")
                //.withMessageGroupId(messageGroupId)
                //.withMessageDeduplicationId(UUID.randomUUID().toString())
                .withMessageBody(body);
        SendMessageResult messageResult = amazonSQSAsync.sendMessage(sendMessageRequest);
        log.debug(LocalDateTime.now() + " SQSUtil::status " + messageResult.getMessageId() + " " + LocalDateTime.now() + ": " + body);
    }

    public static void sendMessage(Map<String, Object> message, String messageGroupId) {
        String body = new Gson().toJson(message);
        SendMessageRequest sendMessageRequest = new SendMessageRequest()
                .withQueueUrl("https://sqs.sa-east-1.amazonaws.com/069251018472/hayiz-doc-response-dev")
                //.withMessageGroupId(messageGroupId)
                //.withMessageDeduplicationId(UUID.randomUUID().toString())
                .withMessageBody(body);
        SendMessageResult messageResult = amazonSQSAsync.sendMessage(sendMessageRequest);
        log.debug(LocalDateTime.now() + " SQSUtil::sendMessage " + messageResult.getMessageId() + " " + LocalDateTime.now() + ": " + body);
    }

    public static void resend(String queueUrl, Message message, String type, List<Map<String, Object>> data, String messageGroupId) {
        if(data.isEmpty()) {
            return;
        }

        System.out.println(LocalDateTime.now() + ": resend: " + message.getAction() + " -> " + data);

        message.getData().put(type, data);

        String body = new Gson().toJson(message);
        SendMessageRequest sendMessageRequest = new SendMessageRequest()
                .withQueueUrl(queueUrl)
                //.withMessageGroupId(messageGroupId)
                //.withMessageGroupId(UUID.randomUUID().toString())
                //.withMessageDeduplicationId(UUID.randomUUID().toString())
                .withDelaySeconds(300)
                .withMessageBody(body);
        //sendMessageRequest.setDelaySeconds(600);
        SendMessageResult messageResult = amazonSQSAsync.sendMessage(sendMessageRequest);
        log.debug(LocalDateTime.now() + " SQSUtil::resend " + messageResult.getMessageId() + " " + LocalDateTime.now() + ": " + body);
    }

    public static void changeMessageVisibility(String queueUrl, String receiptHandle, int visibilityTimeout) {
        System.out.println(LocalDateTime.now() + ": changeMessageVisibility: " + queueUrl + " -> " + visibilityTimeout);
        amazonSQSAsync.changeMessageVisibility(queueUrl, receiptHandle, visibilityTimeout);
        log.debug(LocalDateTime.now() + " SQSUtil::changeMessageVisibility " + LocalDateTime.now() + ": " + receiptHandle);
    }
}
