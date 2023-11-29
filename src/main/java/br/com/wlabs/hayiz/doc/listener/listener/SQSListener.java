package br.com.wlabs.hayiz.doc.listener.listener;

import br.com.wlabs.hayiz.doc.listener.properties.Hayiz;
import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bonigarcia.wdm.managers.ChromeDriverManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

@Service
@RequiredArgsConstructor
public class SQSListener {

    private final AmazonSQSAsync amazonSQSAsync;
    private final ObjectMapper objectMapper;
    private final Hayiz hayiz;

    @PostConstruct
    public void listener() {
        ChromeDriverManager.chromedriver().disableCsp().setup();
        for (String document : this.hayiz.getDocuments()) {
            try {
                String queueName = "hayiz-doc-importer-dev" + "-" + document;
                DocumentRunnable runnable =
                        new DocumentRunnable(queueName, 10, this.amazonSQSAsync, this.objectMapper);
                Thread thread = new Thread(runnable);
                thread.start();
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        }

        /*try {
            String queueName = "hayiz-doc-importer-dev";
            DocumentRunnable runnable =
                    new DocumentRunnable(queueName, 5, this.amazonSQSAsync, this.objectMapper);
            Thread thread = new Thread(runnable);
            thread.start();
        } catch (Exception exception) {
            exception.printStackTrace();
        }*/
    }
}
