package com.hamzaelkhatib.pdfgenerator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class PdfgeneratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(PdfgeneratorApplication.class, args);
    }

}
