package com.hamzaelkhatib.pdfgenerator.model;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
public class GeneratePdfRequest {
	private String dataUrl;
	private int numberOfArticles;
	private final PdfConfig pdfConfig = new PdfConfig(); // Default config if not provided
	private final BatchConfig batchConfig = new BatchConfig(); // Default config if not provided
}