package com.hamzaelkhatib.pdfgenerator.controller;

import com.hamzaelkhatib.pdfgenerator.model.GeneratePdfRequest;
import com.hamzaelkhatib.pdfgenerator.model.JobResponse;
import com.hamzaelkhatib.pdfgenerator.service.PdfGeneratorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pdf")
public class PdfController {

    @Autowired
    private PdfGeneratorService pdfGeneratorService;

    @PostMapping("/generate")
    public ResponseEntity<JobResponse> generatePdf(@RequestBody GeneratePdfRequest request) {
        String jobId = pdfGeneratorService.startPdfGeneration(request.getDataUrl(), request.getNumberOfArticles());
        return ResponseEntity.ok(new JobResponse(jobId));
    }

    @GetMapping("/status/{jobId}")
    public ResponseEntity<?> checkStatus(@PathVariable String jobId) {
        if (pdfGeneratorService.isProcessing(jobId)) {
            return ResponseEntity.status(HttpStatus.PROCESSING).build();
        }

        Resource pdfResource = pdfGeneratorService.getCompletedPdf(jobId);
        if (pdfResource != null) {
            return ResponseEntity.ok()
                    .header("Content-Type", "application/pdf")
                    .body(pdfResource);
        }

        return ResponseEntity.notFound().build();
    }
}