package com.hamzaelkhatib.pdfgenerator.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;

@Slf4j
@Service
public class PdfCompressionService {

	public void compressPdf(Path inputPath, Path outputPath, int quality) throws IOException {
		try (final PDDocument document = PDDocument.load(inputPath.toFile())) {
			final PDFRenderer pdfRenderer = new PDFRenderer(document);

			// Create a new document for compressed output
			final PDDocument compressedDoc = new PDDocument();

			// Process each page
			for (int pageNumber = 0; pageNumber < document.getNumberOfPages(); pageNumber++) {
				// Render page to image with compression
				final BufferedImage image = pdfRenderer.renderImageWithDPI(pageNumber, 150, // Adjust DPI based on
																							// quality
						// parameter (72-300)
						ImageType.RGB);

				// Convert image to compressed bytes
				final ByteArrayOutputStream baos = new ByteArrayOutputStream();
				ImageIO.write(image, "jpeg", baos);

				// Add image as new page to compressed document
				// Implementation depends on your specific requirements
				// You might want to maintain the original page size and layout
			}

			// Save the compressed document
			compressedDoc.save(outputPath.toFile());
		} catch (final IOException e) {
			log.error("Error compressing PDF", e);
			throw e;
		}
	}
}