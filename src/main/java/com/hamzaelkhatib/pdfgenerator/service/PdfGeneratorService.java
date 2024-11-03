package com.hamzaelkhatib.pdfgenerator.service;

import com.microsoft.playwright.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Service
public class PdfGeneratorService {

    @Value("${pdf.storage.path}")
    private String storagePath;

    public String startPdfGeneration(String dataUrl, int numberOfArticles) {
        String jobId = UUID.randomUUID().toString();
        generatePdfAsync(jobId, dataUrl, numberOfArticles);
        return jobId;
    }

    @Async
    public void generatePdfAsync(String jobId, String dataUrl, int numberOfArticles) {
        Path processingPath = Paths.get(storagePath, jobId + "-processing.pdf");
        Path completedPath = Paths.get(storagePath, jobId + "-completed.pdf");

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch();
            Page page = browser.newPage();

            page.navigate(dataUrl);
            page.pdf(new Page.PdfOptions()
                    .setPath(processingPath)
                    .setFormat("A4")
                    .setMargin(new Page.PdfMargins()
                            .setTop(50.0)
                            .setBottom(50.0)
                            .setLeft(50.0)
                            .setRight(50.0))
            );

            // Move processing file to completed
            Files.move(processingPath, completedPath, StandardCopyOption.REPLACE_EXISTING);

            browser.close();
        } catch (Exception e) {
            e.printStackTrace();
            // Handle error - maybe create an error status file
        }
    }

    public boolean isProcessing(String jobId) {
        Path processingPath = Paths.get(storagePath, jobId + "-processing.pdf");
        return Files.exists(processingPath);
    }

    public Resource getCompletedPdf(String jobId) {
        try {
            Path completedPath = Paths.get(storagePath, jobId + "-completed.pdf");
            if (Files.exists(completedPath)) {
                Resource resource = new UrlResource(completedPath.toUri());

                // After successful retrieval, delete the file
                Files.deleteIfExists(completedPath);

                return resource;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}