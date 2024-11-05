package com.hamzaelkhatib.pdfgenerator.model;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
public class MarginConfig {
	private String top = "50";
	private String right = "50";
	private String bottom = "50";
	private String left = "50";
}