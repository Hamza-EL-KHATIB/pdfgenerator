package com.hamzaelkhatib.pdfgenerator.service;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.Margin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.io.File;
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
            BrowserContext context = browser.newContext();
            Page page = context.newPage();

            page.navigate(dataUrl);
            page.waitForLoadState(LoadState.NETWORKIDLE);

            Margin margin = new Margin();
            margin.setTop("50");
            margin.setRight("50");
            margin.setBottom("50");
            margin.setLeft("50");

            page.pdf(new Page.PdfOptions()
                    .setPath(processingPath)
                    .setFormat("a4")
                    .setMargin(margin));

            Files.move(processingPath, completedPath, StandardCopyOption.REPLACE_EXISTING);

            context.close();
            browser.close();
        } catch (Exception e) {
            try {
                Path errorPath = Paths.get(storagePath, jobId + "-error.txt");
                Files.writeString(errorPath, e.getMessage());
                Files.deleteIfExists(processingPath);
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
    }

    public boolean isProcessing(String jobId) {
        Path processingPath = Paths.get(storagePath, jobId + "-processing.pdf");
        return Files.exists(processingPath);
    }

    public Resource getCompletedPdf(String jobId) {
        try {
            File file = Paths.get(storagePath, jobId + "-completed.pdf").toFile();
            if (file.exists()) {
                return new FileSystemResource(file);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}