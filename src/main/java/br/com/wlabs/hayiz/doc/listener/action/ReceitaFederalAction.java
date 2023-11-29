package br.com.wlabs.hayiz.doc.listener.action;

import br.com.wlabs.hayiz.doc.listener.listener.SQSAction;
import br.com.wlabs.hayiz.doc.listener.model.Message;
import br.com.wlabs.hayiz.doc.listener.provider.receitafederal.action.ListarMensagem;
import br.com.wlabs.hayiz.doc.listener.provider.receitafederal.action.PedidoConsultaFiscal;
import br.com.wlabs.hayiz.doc.listener.provider.receitafederal.action.RelatorioDevedor;
import br.com.wlabs.hayiz.doc.listener.util.SQSUtil;
import org.springframework.cloud.aws.messaging.listener.Acknowledgment;

import java.util.HashMap;
import java.util.Map;

public class ReceitaFederalAction implements SQSAction {

    private static final Map<String, SQSAction> actions = new HashMap<>();

    static {
        actions.put("001", new PedidoConsultaFiscal());
        actions.put("002", new RelatorioDevedor());
        actions.put("003", new ListarMensagem());
    }

    @Override
    public void process(Message message, String queueUrl, final String receiptHandle, String messageGroupId, Acknowledgment acknowledgment) throws Exception {
        if(!this.actions.containsKey(message.getAction())) {
            SQSUtil.changeMessageVisibility(queueUrl, receiptHandle, 900);
            throw new Exception("ReceitaFederalAction: Not implemented yet: " +  message.getAction());
        }
        this.actions.get(message.getAction()).process(message, queueUrl, receiptHandle, messageGroupId, acknowledgment);
    }
}
