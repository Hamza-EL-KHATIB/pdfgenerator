package com.hamzaelkhatib.pdfgenerator.model;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
public class PdfConfig {
	private String pageSize = "a4"; // a4, letter, legal, tabloid, ledger
	private String orientation = "portrait"; // portrait, landscape
	private MarginConfig margin = new MarginConfig();
	private boolean compress = true;
	private int compressionQuality = 75; // 0-100
}
