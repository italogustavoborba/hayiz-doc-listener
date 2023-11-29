package br.com.wlabs.hayiz.doc.listener;

import br.com.wlabs.hayiz.doc.listener.service.PdfService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

//@EnableAsync
@EnableScheduling
@SpringBootApplication
public class BotApplication {

	public static void main(String[] args) {
		SpringApplication.run(BotApplication.class, args);
	}

	//@PostConstruct
	public void tmp() throws Exception {
		PdfService.htmlToPdf(new FileInputStream("C:\\Users\\Italo Teixeira\\Downloads\\teste.html"));
	}
}
