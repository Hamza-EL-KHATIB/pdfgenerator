package com.hamzaelkhatib.pdfgenerator.controller;

import com.hamzaelkhatib.pdfgenerator.model.GeneratePdfRequest;
import com.hamzaelkhatib.pdfgenerator.model.JobProgress;
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
		final JobResponse response = this.pdfGeneratorService.startPdfGeneration(request.getDataUrl(),
				request.getNumberOfArticles(), request.getPdfConfig(), request.getBatchConfig());
		return ResponseEntity.ok(response);
	}

	@GetMapping("/status/{jobId}")
	public ResponseEntity<?> checkStatus(@PathVariable String jobId) {
		final JobProgress progress = this.pdfGeneratorService.getJobProgress(jobId);

		if (progress == null) {
			return ResponseEntity.notFound().build();
		}

		if ("PROCESSING".equals(progress.getStatus()) || "QUEUED".equals(progress.getStatus())) {
			return ResponseEntity.status(102).body(progress);
		}

		if ("FAILED".equals(progress.getStatus())) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(progress);
		}

		try {
			final Resource pdfResource = this.pdfGeneratorService.getCompletedPdf(jobId);
			if (pdfResource != null && pdfResource.exists()) {
				return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
						.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + jobId + ".pdf\"")
						.body(pdfResource);
			}
		} catch (final Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(progress);
		}

		return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
	}
}