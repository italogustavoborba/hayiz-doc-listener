package br.com.wlabs.hayiz.doc.listener.action;

import br.com.wlabs.hayiz.doc.listener.listener.SQSAction;
import br.com.wlabs.hayiz.doc.listener.model.Message;
import br.com.wlabs.hayiz.doc.listener.provider.city.pe.belojardim.BeloJardimPernambucoAction;
import br.com.wlabs.hayiz.doc.listener.provider.city.pe.caruaru.CaruaruPernambucoAction;
import br.com.wlabs.hayiz.doc.listener.provider.city.pe.recife.RecifePernambucoAction;
import br.com.wlabs.hayiz.doc.listener.provider.city.pe.toritama.ToritamaPernambucoAction;
import br.com.wlabs.hayiz.doc.listener.provider.sefaz.pe.SefazPernambucoAction;
import br.com.wlabs.hayiz.doc.listener.util.SQSUtil;
import org.springframework.cloud.aws.messaging.listener.Acknowledgment;

import java.util.HashMap;
import java.util.Map;

public class PernambucoAction implements SQSAction {

    private static final Map<String, SQSAction> actions = new HashMap<>();

    static {
        actions.put(null, new SefazPernambucoAction());
        actions.put("04106", new CaruaruPernambucoAction());
        actions.put("11606", new RecifePernambucoAction());
        actions.put("15409", new ToritamaPernambucoAction());
        actions.put("01706", new BeloJardimPernambucoAction());
    }

    @Override
    public void process(Message message, String queueUrl, final String receiptHandle, String messageGroupId, Acknowledgment acknowledgment) throws Exception {
        if(!this.actions.containsKey(message.getCity())) {
            SQSUtil.changeMessageVisibility(queueUrl, receiptHandle, 900);
            throw new Exception("PernambucoAction: Not implemented yet: " + message.getAction());
        }
        this.actions.get(message.getCity()).process(message, queueUrl, receiptHandle, messageGroupId, acknowledgment);
    }
}
