package com.hamzaelkhatib.pdfgenerator.model;

public class GeneratePdfRequest {
    private String dataUrl;
    private int numberOfArticles;

    // Getters and setters
    public String getDataUrl() {
        return dataUrl;
    }

    public void setDataUrl(String dataUrl) {
        this.dataUrl = dataUrl;
    }

    public int getNumberOfArticles() {
        return numberOfArticles;
    }

    public void setNumberOfArticles(int numberOfArticles) {
        this.numberOfArticles = numberOfArticles;
    }
}