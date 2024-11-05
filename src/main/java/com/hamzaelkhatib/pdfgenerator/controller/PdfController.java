package com.hamzaelkhatib.pdfgenerator.controller;

import com.hamzaelkhatib.pdfgenerator.service.PdfCompressionService;
import com.hamzaelkhatib.pdfgenerator.service.PdfGeneratorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Slf4j
public class PdfController {

	@Autowired
	private PdfGeneratorService pdfGeneratorService;

	@Autowired
	private PdfCompressionService pdfCompressionService;

	@Value("${cors.allowed-origin}")
	private String allowedOrigin;

	@GetMapping("/generateRankingPdf")
	public ResponseEntity<Map<String, String>> generatePdf(@RequestParam String dataUrl,
			@RequestParam int numberOfArticles) {

		final String taskId = UUID.randomUUID().toString();

		try {
			// Create pending file using service method
			this.pdfGeneratorService.createPendingFile(taskId);

			// Start async generation
			this.pdfGeneratorService.generatePdfAsync(taskId, dataUrl, numberOfArticles);

			// Set response headers
			final HttpHeaders headers = new HttpHeaders();
			headers.set("Access-Control-Allow-Credentials", "true");
			headers.set("Access-Control-Expose-Headers", "X-Auth-Token");
			headers.set("Access-Control-Allow-Origin", this.allowedOrigin);
			headers.set("Vary", "Origin");

			final Map<String, String> response = new HashMap<>();
			response.put("taskId", "/api/v1/checkPdfStatus?taskId=" + taskId);

			return ResponseEntity.ok().headers(headers).body(response);

		} catch (final IOException e) {
			log.error("Error creating pending file", e);
			throw new RuntimeException("Failed to initialize PDF generation", e);
		}
	}

	@GetMapping("/checkPdfStatus")
	public ResponseEntity<?> checkStatus(@RequestParam String taskId) {
		final Path pendingPath = Paths.get(this.pdfGeneratorService.getStoragePath(), taskId + "-pending.pdf");
		final Path completedPath = Paths.get(this.pdfGeneratorService.getStoragePath(), taskId + "-completed.pdf");
		final Path decompressedPath = Paths.get(this.pdfGeneratorService.getStoragePath(),
				taskId + "-decompressed.pdf");

		try {
			if (Files.exists(completedPath)) {
				// Decompress PDF
				final byte[] decompressedPdf = this.pdfCompressionService.decompressPdf(completedPath);
				Files.write(decompressedPath, decompressedPdf);

				// Delete the compressed version since we'll serve the decompressed one
				Files.deleteIfExists(completedPath);

				// Create resource from decompressed file
				final Resource resource = new FileSystemResource(decompressedPath.toFile());

				// Set up response
				return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
						.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + taskId + ".pdf\"")
						.header("Access-Control-Allow-Origin", this.allowedOrigin)
						.header("Access-Control-Allow-Credentials", "true")
						.header("Access-Control-Expose-Headers", "X-Auth-Token").header("Vary", "Origin")
						.body(resource);

			} else if (Files.exists(pendingPath)) {
				return ResponseEntity.noContent().build();
			} else {
				return ResponseEntity.ok("File removed or does not exist");
			}
		} catch (final IOException e) {
			log.error("Error handling PDF status check", e);
			throw new RuntimeException("Failed to process PDF", e);
		}
	}
}
