package br.com.wlabs.hayiz.doc.listener.listener;

import br.com.wlabs.hayiz.doc.listener.model.Message;
import br.com.wlabs.hayiz.doc.listener.provider.city.pe.agrestina.ExtratoDebitoAgrestina;
import br.com.wlabs.hayiz.doc.listener.provider.city.pe.arcoverde.ExtratoDebitoArcoverde;
import br.com.wlabs.hayiz.doc.listener.provider.city.pe.belojardim.action.ExtratoDebitoBeloJardim;
import br.com.wlabs.hayiz.doc.listener.provider.city.pe.bezerros.ExtratoDebitoBezerros;
import br.com.wlabs.hayiz.doc.listener.provider.city.pe.bomjardim.ExtratoDebitoBomJardim;
import br.com.wlabs.hayiz.doc.listener.provider.city.pe.caruaru.action.AvisosImportantesPrestador;
import br.com.wlabs.hayiz.doc.listener.provider.city.pe.caruaru.action.AvisosImportantesTomador;
import br.com.wlabs.hayiz.doc.listener.provider.city.pe.caruaru.action.ExtratoDebitoCaruaru;
import br.com.wlabs.hayiz.doc.listener.provider.city.pe.catende.ExtratoDebitoCatende;
import br.com.wlabs.hayiz.doc.listener.provider.city.pe.feiranova.ExtratoDebitoFeiraNova;
import br.com.wlabs.hayiz.doc.listener.provider.city.pe.gravata.ExtratoDebitoGravata;
import br.com.wlabs.hayiz.doc.listener.provider.city.pe.ilhadeitamaraca.ExtratoDebitoIlhaDeItamaraca;
import br.com.wlabs.hayiz.doc.listener.provider.city.pe.lagoadocarro.ExtratoDebitoLagoaDoCarro;
import br.com.wlabs.hayiz.doc.listener.provider.city.pe.moreno.ExtratoDebitoMoreno;
import br.com.wlabs.hayiz.doc.listener.provider.city.pe.orobo.ExtratoDebitoOrobo;
import br.com.wlabs.hayiz.doc.listener.provider.city.pe.ouricuri.ExtratoDebitoOuricuri;
import br.com.wlabs.hayiz.doc.listener.provider.city.pe.paudalho.ExtratoDebitoPaudalho;
import br.com.wlabs.hayiz.doc.listener.provider.city.pe.pesqueira.ExtratoDebitoPesqueira;
import br.com.wlabs.hayiz.doc.listener.provider.city.pe.pombos.ExtratoDebitoPombos;
import br.com.wlabs.hayiz.doc.listener.provider.city.pe.recife.action.ExtratoDebitoRecife;
import br.com.wlabs.hayiz.doc.listener.provider.city.pe.saocaetano.ExtratoDebitoSaoCaitano;
import br.com.wlabs.hayiz.doc.listener.provider.city.pe.saojosedacoroagrande.ExtratoDebitoSaoJoseDaCoroaGrande;
import br.com.wlabs.hayiz.doc.listener.provider.city.pe.saolourenco.ExtratoDebitoSaoLourenco;
import br.com.wlabs.hayiz.doc.listener.provider.city.pe.toritama.action.ExtratoDebitoToritama;
import br.com.wlabs.hayiz.doc.listener.provider.integracontador.action.PedidoConsultaFiscal;
import br.com.wlabs.hayiz.doc.listener.provider.receitafederal.action.ListarMensagem;
import br.com.wlabs.hayiz.doc.listener.provider.receitafederal.action.RelatorioDevedor;
import br.com.wlabs.hayiz.doc.listener.provider.sefaz.pe.action.*;
import br.com.wlabs.hayiz.doc.listener.util.SQSUtil;
import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.model.GetQueueUrlResult;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.aws.messaging.listener.Acknowledgment;
import org.springframework.cloud.aws.messaging.listener.QueueMessageAcknowledgment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
public class DocumentRunnable implements Runnable {

    private final ThreadPoolTaskExecutor threadPoolTaskExecutor;
    private final AmazonSQSAsync amazonSQSAsync;
    private final ObjectMapper objectMapper;
    private final Map<String, SQSAction> actions = new HashMap<>();
    private final int taskCount;
    private final String queueUrl;

    public DocumentRunnable(String queueName, int taskCount, AmazonSQSAsync amazonSQSAsync, ObjectMapper objectMapper) {
        this.taskCount = taskCount;
        this.amazonSQSAsync = amazonSQSAsync;
        this.objectMapper = objectMapper;

        GetQueueUrlResult getQueueUrlResult =
                this.amazonSQSAsync.getQueueUrl(queueName);
        this.queueUrl = getQueueUrlResult.getQueueUrl();

        this.threadPoolTaskExecutor = new ThreadPoolTaskExecutor();
        this.threadPoolTaskExecutor.setCorePoolSize(taskCount / 2);
        this.threadPoolTaskExecutor.setMaxPoolSize(taskCount);
        this.threadPoolTaskExecutor.setThreadNamePrefix(this.getClass().getName() + "-");
        this.threadPoolTaskExecutor.setQueueCapacity(0);
        this.threadPoolTaskExecutor.setDaemon(true);
        this.threadPoolTaskExecutor.initialize();

        this.actions.put("001", new PREmitirCertidaoCadastro());
        this.actions.put("002", new PRConsultarIrregularidadesContribuinte());
        this.actions.put("003", new PRConsultarDadosEconomicosContribuinte());
        this.actions.put("004", new PREmitirExtratosContribuinteDAE());
        this.actions.put("005", new PREmitirExtratosContribuinteNaoCalculadas());
        this.actions.put("006", new PedidoConsultaFiscal());
        this.actions.put("007", new RelatorioDevedor());
        this.actions.put("008", new ListarMensagem());
        this.actions.put("009", new PRConsultarIntimacaoContribuinteGMF());
        this.actions.put("010", new AvisosImportantesPrestador());
        this.actions.put("011", new AvisosImportantesTomador());
        this.actions.put("012", new ExtratoDebitoCaruaru());
        this.actions.put("013", new ExtratoDebitoToritama());
        this.actions.put("014", new ExtratoDebitoBeloJardim());
        this.actions.put("015", new ExtratoDebitoRecife());

        this.actions.put("016", new ExtratoDebitoPesqueira());
        this.actions.put("017", new ExtratoDebitoOrobo());
        this.actions.put("018", new ExtratoDebitoGravata());
        this.actions.put("019", new ExtratoDebitoPombos());
        this.actions.put("020", new ExtratoDebitoFeiraNova());
        this.actions.put("021", new ExtratoDebitoIlhaDeItamaraca());
        this.actions.put("022", new ExtratoDebitoBomJardim());
        this.actions.put("023", new ExtratoDebitoSaoLourenco());
        this.actions.put("024", new ExtratoDebitoPaudalho());
        this.actions.put("025", new ExtratoDebitoLagoaDoCarro());
        this.actions.put("026", new ExtratoDebitoMoreno());
        this.actions.put("027", new ExtratoDebitoCatende());
        this.actions.put("028", new ExtratoDebitoSaoJoseDaCoroaGrande());
        this.actions.put("029", new ExtratoDebitoSaoCaitano());
        this.actions.put("030", new ExtratoDebitoAgrestina());
        this.actions.put("031", new ExtratoDebitoBezerros());
        this.actions.put("032", new ExtratoDebitoArcoverde());
        this.actions.put("033", new ExtratoDebitoOuricuri());

        this.actions.put("DIAC", new PREmitirDIAC());
        this.actions.put("DAE10", new PREmitirDAEProcessoFiscal());
    }

    public void run() {
        while (true) {
            try {
                int availableTasks = this.taskCount - this.threadPoolTaskExecutor.getThreadPoolExecutor().getActiveCount();
                if (availableTasks <= 0) continue;

                ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest()
                        .withAttributeNames("All")
                        .withMessageAttributeNames("All");
                receiveMessageRequest.setQueueUrl(this.queueUrl);

                int messagesSize;
                do {
                    receiveMessageRequest.setMaxNumberOfMessages(availableTasks >= 10 ? 10 : availableTasks);
                    ReceiveMessageResult receiveMessageResult = this.amazonSQSAsync
                            .receiveMessage(receiveMessageRequest);
                    receiveMessageResult.getMessages().forEach(message -> this.process(message, this.queueUrl));
                    messagesSize = receiveMessageResult.getMessages().size();
                    availableTasks -= receiveMessageResult.getMessages().size();
                } while (messagesSize > 0 && availableTasks > 0);

            } catch (Exception exception) {
                exception.printStackTrace();
            }

            try {
                TimeUnit.SECONDS.sleep(60);
            } catch (Exception exception) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void process(com.amazonaws.services.sqs.model.Message messageSQS, String queueUrl) {
        try {
            this.threadPoolTaskExecutor.execute(() -> {
                try {
                    String body = messageSQS.getBody();
                    Message message = this.objectMapper.readValue(body, Message.class);

                    String messageGroupId = messageSQS.getAttributes()
                            .getOrDefault("MessageGroupId", message.getId());

                    if (!this.actions.containsKey(message.getAction())) {
                        SQSUtil.changeMessageVisibility(queueUrl, messageSQS.getReceiptHandle(), 900);
                        throw new Exception(LocalDateTime.now() + " Not implemented yet: " + message.getAction());
                    }

                    log.debug(LocalDateTime.now() + " SQSListener::messageResponse(message = " + message + ", " +
                            "header = " + messageSQS.getAttributes() + ")");
                    SQSUtil.changeMessageVisibility(queueUrl, messageSQS.getReceiptHandle(), 120);
                    Acknowledgment acknowledgment = new QueueMessageAcknowledgment(this.amazonSQSAsync, queueUrl,
                            messageSQS.getReceiptHandle());
                    this.actions.get(message.getAction()).process(message, queueUrl, messageSQS.getReceiptHandle(),
                            messageGroupId, acknowledgment);
                } catch (Exception exception) {
                    SQSUtil.changeMessageVisibility(queueUrl, messageSQS.getReceiptHandle(), 500);
                    log.error(LocalDateTime.now() + " SQSListener::messageResponse(message = " + messageSQS + ", " +
                            "header = " + messageSQS.getAttributes() + ")" +
                            " throw " + exception);
                }
            });
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }
}
