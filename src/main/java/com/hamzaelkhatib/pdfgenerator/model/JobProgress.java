package com.hamzaelkhatib.pdfgenerator.model;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
public class JobProgress {
	private String jobId;
	private String status; // QUEUED, PROCESSING, COMPLETED, FAILED
	private int processedItems;
	private int totalItems;
	private String error;
	private double progressPercentage;

	public void updateProgress(int processed, int total) {
		this.processedItems = processed;
		this.totalItems = total;
		this.progressPercentage = total > 0 ? (processed * 100.0) / total : 0;
	}
}
