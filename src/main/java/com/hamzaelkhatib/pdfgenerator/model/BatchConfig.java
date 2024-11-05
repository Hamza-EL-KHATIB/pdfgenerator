package com.hamzaelkhatib.pdfgenerator.model;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
public class BatchConfig {
	private int batchSize = 1;
	private int maxRetries = 3;
	private int retryDelaySeconds = 5;
}