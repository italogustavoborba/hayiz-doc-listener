package br.com.wlabs.hayiz.doc.listener.provider.city.pe.caruaru;

import br.com.wlabs.hayiz.doc.listener.listener.SQSAction;
import br.com.wlabs.hayiz.doc.listener.model.Message;
import br.com.wlabs.hayiz.doc.listener.provider.city.pe.caruaru.action.AvisosImportantesPrestador;
import br.com.wlabs.hayiz.doc.listener.provider.city.pe.caruaru.action.AvisosImportantesTomador;
import br.com.wlabs.hayiz.doc.listener.provider.city.pe.caruaru.action.ExtratoDebitoCaruaru;
import org.springframework.cloud.aws.messaging.listener.Acknowledgment;

import java.util.HashMap;
import java.util.Map;

public class CaruaruPernambucoAction implements SQSAction {

    private static final Map<String, SQSAction> actions = new HashMap<>();

    static {
        actions.put("001", new AvisosImportantesPrestador());
        actions.put("002", new AvisosImportantesTomador());
        actions.put("003", new ExtratoDebitoCaruaru());
    }

    @Override
    public void process(Message message, String queueUrl, final String receiptHandle, String messageGroupId, Acknowledgment acknowledgment) throws Exception {
        if(!this.actions.containsKey(message.getAction())) {
            acknowledgment.acknowledge();
            //SQSUtil.changeMessageVisibility(queueUrl, receiptHandle, 900);
            throw new Exception("CaruaruPernambucoAction: Not implemented yet: " +  message.getAction());
        }
        this.actions.get(message.getAction()).process(message, queueUrl, receiptHandle, messageGroupId, acknowledgment);
    }
}
