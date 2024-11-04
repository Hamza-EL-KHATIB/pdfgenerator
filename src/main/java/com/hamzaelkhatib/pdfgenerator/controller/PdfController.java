package com.hamzaelkhatib.pdfgenerator.controller;

import com.hamzaelkhatib.pdfgenerator.model.GeneratePdfRequest;
import com.hamzaelkhatib.pdfgenerator.model.JobResponse;
import com.hamzaelkhatib.pdfgenerator.service.PdfGeneratorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
            return ResponseEntity.status(102).build(); // 102 Processing
        }

        try {
            Resource pdfResource = pdfGeneratorService.getCompletedPdf(jobId);
            if (pdfResource != null && pdfResource.exists()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_PDF)
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + jobId + ".pdf\"")
                        .body(pdfResource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}