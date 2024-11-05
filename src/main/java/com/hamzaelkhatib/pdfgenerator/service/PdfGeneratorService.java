package com.hamzaelkhatib.pdfgenerator.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.Margin;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class PdfGeneratorService {

	@Getter
	@Value("${pdf.storage.path}")
	private String storagePath;

	@Value("${pdf.cleanup.retention-minutes:30}")
	private int retentionMinutes;

	@Value("${pdf.ranking.root}")
	private String rootUrl;

	@Value("${pdf.ranking.articles-per-pdf:200}")
	private int articlesPerPdf;

	@Value("${pdf.ranking.batch-size:2}")
	private int batchSize;

	private final PdfCompressionService pdfCompressionService;

	@PostConstruct
	public void init() {
		try {
			Files.createDirectories(Paths.get(this.storagePath));
		} catch (final IOException e) {
			log.error("Failed to create storage directory", e);
		}
	}

	public void createPendingFile(String taskId) throws IOException {
		final Path pendingPath = Paths.get(this.storagePath, taskId + "-pending.pdf");
		Files.createFile(pendingPath);
	}

	@Async
	public void generatePdfAsync(String taskId, String dataUrl, int numberOfArticles) {
		final Path processingPath = Paths.get(this.storagePath, taskId + "-processing.pdf");
		final Path completedPath = Paths.get(this.storagePath, taskId + "-completed.pdf");
		final Path pendingPath = Paths.get(this.storagePath, taskId + "-pending.pdf");
		final List<Path> chunkPaths = new ArrayList<>();

		try {
			// Generate URLs for each chunk
			final List<String> urls = new ArrayList<>();
			for (int offset = 0; offset < numberOfArticles; offset += this.articlesPerPdf) {
				final int limit = Math.min(this.articlesPerPdf, numberOfArticles - offset);
				final String url = String.format("%s/%d/%d?dataUrl=%s", this.rootUrl, offset, limit,
						URLEncoder.encode(dataUrl, StandardCharsets.UTF_8));
				urls.add(url);
			}

			log.info("Generated {} chunks for {} articles", urls.size(), numberOfArticles);

			// Process chunks in batches
			for (int i = 0; i < urls.size(); i += this.batchSize) {
				final int batchIndex = i;
				final List<String> batchUrls = urls.subList(i, Math.min(i + this.batchSize, urls.size()));
				final List<CompletableFuture<Path>> batchFutures = batchUrls.stream()
						.map(url -> CompletableFuture.supplyAsync(() -> this.generateChunk(taskId, url, batchIndex)))
						.collect(Collectors.toList());

				// Wait for batch completion
				CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0])).join();

				// Collect successful chunks
				for (final CompletableFuture<Path> future : batchFutures) {
					try {
						final Path chunkPath = future.get();
						if (chunkPath != null) {
							chunkPaths.add(chunkPath);
						}
					} catch (final Exception e) {
						log.error("Chunk generation failed", e);
					}
				}
			}

			// Merge chunks if we have any
			if (!chunkPaths.isEmpty()) {
				this.mergePdfChunks(chunkPaths, completedPath);

				// Cleanup chunks after successful merge
				for (final Path chunkPath : chunkPaths) {
					Files.deleteIfExists(chunkPath);
				}

				// After successful completion, remove pending file
				Files.deleteIfExists(pendingPath);
			} else {
				log.error("No PDF chunks were successfully generated for taskId: " + taskId);
			}

		} catch (final Exception e) {
			log.error("Error generating PDF for taskId: " + taskId, e);
		} finally {
			try {
				Files.deleteIfExists(processingPath);
			} catch (final IOException e) {
				log.error("Error cleaning up processing file for taskId: " + taskId, e);
			}
		}
	}

	private Path generateChunk(String taskId, String url, int index) {
		final Path chunkPath = Paths.get(this.storagePath, taskId + "-chunk-" + index + ".pdf");

		try (final Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium().launch();
			final BrowserContext context = browser.newContext();
			final Page page = context.newPage();

			page.navigate(url);
			page.waitForLoadState(LoadState.NETWORKIDLE);

			page.pdf(new Page.PdfOptions().setPath(chunkPath).setFormat("A4")
					.setMargin(new Margin().setTop("1cm").setRight("1cm").setBottom("1cm").setLeft("1cm")));

			context.close();
			browser.close();
			return chunkPath;

		} catch (final Exception e) {
			log.error("Error generating chunk for index: " + index, e);
			return null;
		}
	}

	private void mergePdfChunks(List<Path> chunkPaths, Path outputPath) throws IOException {
		try (final PDDocument mergedDoc = new PDDocument()) {
			for (final Path chunkPath : chunkPaths) {
				try (final PDDocument chunkDoc = PDDocument.load(chunkPath.toFile())) {
					for (int i = 0; i < chunkDoc.getNumberOfPages(); i++) {
						mergedDoc.importPage(chunkDoc.getPage(i));
					}
				}
			}
			// Save as compressed
			this.pdfCompressionService.savePdfCompressed(mergedDoc, outputPath);
		}
	}

	@Scheduled(fixedDelayString = "${pdf.cleanup.interval-minutes:2}000")
	public void cleanupOldFiles() {
		try {
			final LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(this.retentionMinutes);

			Files.walk(Paths.get(this.storagePath)).filter(Files::isRegularFile).filter(path -> {
				try {
					return Files.getLastModifiedTime(path).toInstant().isBefore(cutoffTime.toInstant(ZoneOffset.UTC));
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
}