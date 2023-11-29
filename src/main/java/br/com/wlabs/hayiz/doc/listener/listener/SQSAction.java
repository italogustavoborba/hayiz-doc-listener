package br.com.wlabs.hayiz.doc.listener.listener;

import br.com.wlabs.hayiz.doc.listener.model.Message;
import org.springframework.cloud.aws.messaging.listener.Acknowledgment;

public interface SQSAction {

    void process(Message message, String queueUrl, final String receiptHandle, String messageGroupId, Acknowledgment acknowledgment) throws Exception;
}
