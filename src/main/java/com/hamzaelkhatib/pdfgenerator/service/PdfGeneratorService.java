package com.hamzaelkhatib.pdfgenerator.service;

import com.hamzaelkhatib.pdfgenerator.model.BatchConfig;
import com.hamzaelkhatib.pdfgenerator.model.JobProgress;
import com.hamzaelkhatib.pdfgenerator.model.JobResponse;
import com.hamzaelkhatib.pdfgenerator.model.PdfConfig;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.Margin;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class PdfGeneratorService {

	@Value("${pdf.storage.path}")
	private String storagePath;

	@Value("${pdf.cleanup.retention-minutes:1440}")
	private int retentionMinutes;

	private final Map<String, JobProgress> jobProgressMap = new ConcurrentHashMap<>();
	private final PdfCompressionService pdfCompressionService;

	public PdfGeneratorService(PdfCompressionService pdfCompressionService) {
		this.pdfCompressionService = pdfCompressionService;
	}

	@PostConstruct
	public void init() {
		try {
			Files.createDirectories(Paths.get(this.storagePath));
		} catch (final IOException e) {
			log.error("Failed to create storage directory", e);
		}
	}

	public JobResponse startPdfGeneration(String dataUrl, int numberOfArticles, PdfConfig pdfConfig,
			BatchConfig batchConfig) {
		final String jobId = UUID.randomUUID().toString();

		final JobProgress progress = new JobProgress();
		progress.setJobId(jobId);
		progress.setStatus("QUEUED");
		progress.setTotalItems(numberOfArticles);
		this.jobProgressMap.put(jobId, progress);

		this.generatePdfAsync(jobId, dataUrl, numberOfArticles, pdfConfig, batchConfig);

		final JobResponse response = new JobResponse();
		response.setJobId(jobId);
		response.setProgress(progress);
		return response;
	}

	@Async
	public void generatePdfAsync(String jobId, String dataUrl, int numberOfArticles, PdfConfig pdfConfig,
			BatchConfig batchConfig) {
		final JobProgress progress = this.jobProgressMap.get(jobId);
		progress.setStatus("PROCESSING");

		final Path processingPath = Paths.get(this.storagePath, jobId + "-processing.pdf");
		final Path completedPath = Paths.get(this.storagePath, jobId + "-completed.pdf");

		int retryCount = 0;
		boolean success = false;

		while (!success && retryCount < batchConfig.getMaxRetries()) {
			try (final Playwright playwright = Playwright.create()) {
				final Browser browser = playwright.chromium().launch();
				final BrowserContext context = browser.newContext();
				final Page page = context.newPage();

				page.navigate(dataUrl);
				page.waitForLoadState(LoadState.NETWORKIDLE);

				// Configure margins
				final Margin margin = new Margin();
				margin.setTop(pdfConfig.getMargin().getTop());
				margin.setRight(pdfConfig.getMargin().getRight());
				margin.setBottom(pdfConfig.getMargin().getBottom());
				margin.setLeft(pdfConfig.getMargin().getLeft());

				// Configure PDF options
				final Page.PdfOptions pdfOptions = new Page.PdfOptions().setPath(processingPath)
						.setFormat(pdfConfig.getPageSize())
						.setLandscape("landscape".equalsIgnoreCase(pdfConfig.getOrientation())).setMargin(margin);

				page.pdf(pdfOptions);

				// Compress PDF if requested
				if (pdfConfig.isCompress()) {
					this.pdfCompressionService.compressPdf(processingPath, processingPath,
							pdfConfig.getCompressionQuality());
				}

				Files.move(processingPath, completedPath, StandardCopyOption.REPLACE_EXISTING);

				context.close();
				browser.close();

				success = true;
				progress.setStatus("COMPLETED");
				progress.updateProgress(numberOfArticles, numberOfArticles);

			} catch (final Exception e) {
				log.error("Error generating PDF (attempt " + (retryCount + 1) + ")", e);
				retryCount++;

				if (retryCount >= batchConfig.getMaxRetries()) {
					progress.setStatus("FAILED");
					progress.setError(e.getMessage());
					try {
						final Path errorPath = Paths.get(this.storagePath, jobId + "-error.txt");
						Files.writeString(errorPath, e.getMessage());
						Files.deleteIfExists(processingPath);
					} catch (final IOException ioe) {
						log.error("Error writing error file", ioe);
					}
				} else {
					try {
						Thread.sleep(batchConfig.getRetryDelaySeconds() * 1000L);
					} catch (final InterruptedException ie) {
						Thread.currentThread().interrupt();
						break;
					}
				}
			}
		}
	}

	@Scheduled(fixedDelayString = "${pdf.cleanup.interval-minutes:60}000")
	public void cleanupOldFiles() {
		try {
			final LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(this.retentionMinutes);

			Files.walk(Paths.get(this.storagePath)).filter(Files::isRegularFile).filter(path -> {
				try {
					return Files.getLastModifiedTime(path).toInstant()
							.isBefore(cutoffTime.toInstant(java.time.ZoneOffset.UTC));
				} catch (final IOException e) {
					return false;
				}
			}).forEach(path -> {
				try {
					Files.delete(path);
					log.info("Deleted old file: {}", path);
				} catch (final IOException e) {
					log.error("Error deleting file: {}", path, e);
				}
			});
		} catch (final IOException e) {
			log.error("Error during cleanup", e);
		}
	}

	public JobProgress getJobProgress(String jobId) {
		return this.jobProgressMap.get(jobId);
	}

	public boolean isProcessing(String jobId) {
		final JobProgress progress = this.jobProgressMap.get(jobId);
		return progress != null && "PROCESSING".equals(progress.getStatus());
	}

	public Resource getCompletedPdf(String jobId) {
		try {
			final File file = Paths.get(this.storagePath, jobId + "-completed.pdf").toFile();
			if (file.exists()) {
				return new FileSystemResource(file);
			}
		} catch (final Exception e) {
			log.error("Error retrieving completed PDF", e);
		}
		return null;
	}
}