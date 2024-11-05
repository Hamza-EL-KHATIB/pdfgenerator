package com.hamzaelkhatib.pdfgenerator.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;

@Slf4j
@Service
public class PdfCompressionService {

	public byte[] decompressPdf(Path compressedPath) throws IOException {
		try (final PDDocument pdfDoc = PDDocument.load(compressedPath.toFile())) {
			final ByteArrayOutputStream output = new ByteArrayOutputStream();
			pdfDoc.save(output);
			return output.toByteArray();
		} catch (final IOException e) {
			log.error("Error decompressing PDF", e);
			throw e;
		}
	}

	public void savePdfCompressed(PDDocument document, Path outputPath) throws IOException {
		document.save(outputPath.toFile());
	}
}