package br.com.wlabs.hayiz.doc.listener.provider.city.pe.belojardim;

import br.com.wlabs.hayiz.doc.listener.listener.SQSAction;
import br.com.wlabs.hayiz.doc.listener.model.Message;
import br.com.wlabs.hayiz.doc.listener.provider.city.pe.belojardim.action.ExtratoDebitoBeloJardim;
import br.com.wlabs.hayiz.doc.listener.util.SQSUtil;
import org.springframework.cloud.aws.messaging.listener.Acknowledgment;

import java.util.HashMap;
import java.util.Map;

public class BeloJardimPernambucoAction implements SQSAction {

    private static final Map<String, SQSAction> actions = new HashMap<>();

    static {
        actions.put("001", new ExtratoDebitoBeloJardim());
    }

    @Override
    public void process(Message message, String queueUrl, final String receiptHandle, String messageGroupId, Acknowledgment acknowledgment) throws Exception {
        if(!this.actions.containsKey(message.getAction())) {
            //acknowledgment.acknowledge();
            SQSUtil.changeMessageVisibility(queueUrl, receiptHandle, 900);
            throw new Exception("BeloJardimPernambucoAction: Not implemented yet: " +  message.getAction());
        }
        this.actions.get(message.getAction()).process(message, queueUrl, receiptHandle, messageGroupId, acknowledgment);
    }
}
