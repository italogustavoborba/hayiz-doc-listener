package br.com.wlabs.hayiz.doc.listener.provider.sefaz.pe;

import br.com.wlabs.hayiz.doc.listener.listener.SQSAction;
import br.com.wlabs.hayiz.doc.listener.model.Message;
import br.com.wlabs.hayiz.doc.listener.provider.sefaz.pe.action.*;
import br.com.wlabs.hayiz.doc.listener.util.SQSUtil;
import org.springframework.cloud.aws.messaging.listener.Acknowledgment;

import java.util.HashMap;
import java.util.Map;

public class SefazPernambucoAction implements SQSAction {

    private static final Map<String, SQSAction> actions = new HashMap<>();

    static {
        actions.put("001", new PREmitirCertidaoCadastro());
        actions.put("002", new PRConsultarIrregularidadesContribuinte());
        actions.put("003", new PRConsultarDadosEconomicosContribuinte());
        actions.put("004", new PREmitirExtratosContribuinteDAE());
        actions.put("005", new PREmitirExtratosContribuinteNaoCalculadas());
        actions.put("006", new PRConsultarIntimacaoContribuinteGMF());
        actions.put("DIAC", new PREmitirDIAC());
        actions.put("DAE10", new PREmitirDAEProcessoFiscal());
    }

    @Override
    public void process(Message message, String queueUrl, final String receiptHandle, String messageGroupId, Acknowledgment acknowledgment) throws Exception {
        if(!this.actions.containsKey(message.getAction())) {
            SQSUtil.changeMessageVisibility(queueUrl, receiptHandle, 900);
            throw new Exception("SefazPernambucoAction: Not implemented yet: " +  message.getAction());
        }
        this.actions.get(message.getAction()).process(message, queueUrl, receiptHandle, messageGroupId, acknowledgment);
    }
}
