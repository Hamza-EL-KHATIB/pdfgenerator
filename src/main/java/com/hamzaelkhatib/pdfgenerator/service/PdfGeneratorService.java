package com.hamzaelkhatib.pdfgenerator.service;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.Margin;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
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
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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

	@Value("${pdf.ranking.domain}")
	private String domain;

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
	public void generatePdfAsync(String taskId, String dataUrl, int numberOfArticles, String cookie) {
		try {
			// Generate all URLs for chunks
			final List<String> urls = this.generateUrls(dataUrl, numberOfArticles);
			log.info("Generated {} chunk URLs for {} articles", urls.size(), numberOfArticles);

			// List to store successfully generated chunks
			final List<Path> successfulChunks = new ArrayList<>();

			// Process chunks in sequential batches
			for (int startIndex = 0; startIndex < urls.size(); startIndex += this.batchSize) {
				final int currentBatchStart = startIndex;
				final List<String> batchUrls = urls.subList(startIndex,
						Math.min(startIndex + this.batchSize, urls.size()));

				// Process each batch asynchronously
				final List<CompletableFuture<Path>> batchFutures = batchUrls.stream()
						.map(url -> CompletableFuture
								.supplyAsync(() -> this.generateChunk(taskId, url, currentBatchStart, cookie)))
						.collect(Collectors.toList());

				// Wait for this batch to complete
				CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0])).join();

				// Collect paths of successfully generated chunks
				for (final CompletableFuture<Path> future : batchFutures) {
					try {
						final Path chunkPath = future.get();
						if (chunkPath != null && Files.exists(chunkPath)) {
							successfulChunks.add(chunkPath);
							log.info("Successfully generated chunk: {}", chunkPath.getFileName());
						}
					} catch (final Exception e) {
						log.error("Failed to get chunk result", e);
					}
				}
			}

			// Only merge if we have successful chunks
			if (!successfulChunks.isEmpty()) {
				log.info("Starting merge of {} chunks", successfulChunks.size());
				final Path completedPath = Paths.get(this.storagePath, taskId + "-completed.pdf");

				// Execute synchronous merging
				final boolean mergeSuccess = this.mergeChunks(successfulChunks, completedPath);

				if (mergeSuccess) {
					log.info("Successfully merged {} chunks into final PDF", successfulChunks.size());

					// Cleanup chunks after merging
					for (final Path chunk : successfulChunks) {
						try {
							Files.deleteIfExists(chunk);
						} catch (final IOException e) {
							log.warn("Failed to delete chunk: {}", chunk, e);
						}
					}

					// Remove the pending file
					Files.deleteIfExists(Paths.get(this.storagePath, taskId + "-pending.pdf"));
				}
			} else {
				log.error("No chunks were successfully generated for taskId: {}", taskId);
			}

		} catch (final Exception e) {
			log.error("Error in PDF generation process for taskId: {}", taskId, e);
		}
	}

	private List<String> generateUrls(String dataUrl, int numberOfArticles) {
		final List<String> urls = new ArrayList<>();
		for (int offset = 0; offset < numberOfArticles; offset += this.articlesPerPdf) {
			final int limit = Math.min(this.articlesPerPdf, numberOfArticles - offset);
			final String url = String.format("%s/rankcomda/pdf/%d/%d?dataUrl=%s", this.rootUrl, offset, limit,
					URLEncoder.encode(dataUrl, StandardCharsets.UTF_8));
			urls.add(url);
		}
		return urls;
	}

	private Path generateChunk(String taskId, String url, int index, String cookie) {
		final Path chunkPath = Paths.get(this.storagePath, taskId + "-chunk-" + index + ".pdf");

		try (final Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(false));
			final BrowserContext context = browser.newContext();

			if (cookie != null && !cookie.isEmpty()) {
				final List<Cookie> cookies = this.parseCookieString(cookie);
				context.addCookies(cookies);
				log.info("Added {} cookies to context", cookies.size());
			}

			final Page page = context.newPage();
			page.navigate(url);
			page.waitForLoadState(LoadState.NETWORKIDLE);

			// Wait for all images to load using inline function code
			page.waitForFunction("() => new Promise(resolve => {"
					+ "  const images = Array.from(document.querySelectorAll('img'));"
					+ "  if (images.length === 0) resolve();" + "  let loadedCount = 0;" + "  images.forEach(img => {"
					+ "    if (img.complete) {" + "      loadedCount++;"
					+ "      if (loadedCount === images.length) resolve();" + "    } else {"
					+ "      img.addEventListener('load', () => {" + "        loadedCount++;"
					+ "        if (loadedCount === images.length) resolve();" + "      });"
					+ "      img.addEventListener('error', () => {" + "        loadedCount++;"
					+ "        if (loadedCount === images.length) resolve();" + "      });" + "    }" + "  });" + "})");

			// Generate PDF with loaded images
			page.pdf(new Page.PdfOptions().setPath(chunkPath).setFormat("A4").setPrintBackground(true) // Ensure
																										// background
																										// images/colors
																										// are included
					.setMargin(new Margin().setTop("1cm").setRight("1cm").setBottom("1cm").setLeft("1cm")));

			context.close();
			browser.close();
			return chunkPath;

		} catch (final Exception e) {
			log.error("Error generating chunk for index: {}", index, e);
			return null;
		}
	}

	private boolean mergeChunks(List<Path> chunkPaths, Path outputPath) {
		log.info("Starting PDF merge of {} chunks into {}", chunkPaths.size(), outputPath);

		final PDFMergerUtility merger = new PDFMergerUtility();
		merger.setDestinationFileName(outputPath.toString());

		try {
			// Sort paths by their index to ensure correct order
			chunkPaths.sort(Comparator.comparingInt(this::extractChunkIndex));

			for (final Path chunkPath : chunkPaths) {
				merger.addSource(chunkPath.toFile());
			}

			// Perform the merge
			merger.mergeDocuments(null); // Use null or MemoryUsageSetting.setupMainMemoryOnly() for simplicity
			log.info("Successfully merged PDFs into {}", outputPath);
			return true;

		} catch (final IOException e) {
			log.error("Error during PDF merge", e);
			return false;
		}
	}

	// Helper method to extract chunk index from the filename
	private int extractChunkIndex(Path path) {
		final String filename = path.getFileName().toString();
		final String[] parts = filename.split("-");
		try {
			return Integer.parseInt(parts[parts.length - 1].replace(".pdf", ""));
		} catch (final NumberFormatException e) {
			log.warn("Failed to parse chunk index from filename: {}", filename);
			return -1; // Defaults to lowest priority if parsing fails
		}
	}

	private List<Cookie> parseCookieString(String cookieString) {
		return Arrays.stream(cookieString.split(";")).map(String::trim).map(cookie -> {
			final String[] parts = cookie.split("=", 2);
			final String name = parts[0];
			final String value = parts.length > 1 ? parts[1] : "";
			return new Cookie(name, value).setDomain(this.domain).setPath("/").setSecure(true).setHttpOnly(true);
		}).collect(Collectors.toList());
	}

	@Scheduled(fixedDelay = 600_000) // Run every 10 minutes (600,000 ms)
	public void cleanupOldFiles() {
		try {
			final LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(30); // Delete files older than 30 minutes
			log.info("Running cleanup for files older than 30 minutes");

			Files.list(Paths.get(this.storagePath)).filter(Files::isRegularFile)
					.filter(path -> this.isOlderThanRetention(path, cutoffTime)).forEach(this::deleteFileSafely);

		} catch (final IOException e) {
			log.error("Error during cleanup", e);
		}
	}

	// Helper method to check if a file is older than the retention time
	private boolean isOlderThanRetention(Path path, LocalDateTime cutoffTime) {
		try {
			final FileTime fileTime = Files.getLastModifiedTime(path);
			return fileTime.toInstant().isBefore(cutoffTime.toInstant(ZoneOffset.UTC));
		} catch (final IOException e) {
			log.warn("Failed to get last modified time for {}", path, e);
			return false; // Skip file if there's an error
		}
	}

	// Helper method to delete files and log success/failure
	private void deleteFileSafely(Path path) {
		try {
			Files.deleteIfExists(path);
			log.info("Deleted old file: {}", path);
		} catch (final IOException e) {
			log.error("Failed to delete file: {}", path, e);
		}
	}

}