package com.hamzaelkhatib.pdfgenerator.model;

public class JobResponse {
    private String jobId;

    public JobResponse(String jobId) {
        this.jobId = jobId;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }
}