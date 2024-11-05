# PDF Generator

A Spring Boot application for generating PDFs from web content using Playwright and PDFBox.

## 📋 Table of Contents
- [1. Project Overview](#1-project-overview)
    - [Project Structure](#project-structure)
    - [Dependencies](#dependencies)
- [2. Configuration](#2-configuration)
- [3. Setup Instructions](#3-setup-instructions)
- [4. API Documentation](#4-api-documentation)
- [5. Process Flow](#5-process-flow)

## 1. Project Overview

### Project Structure
```plaintext
pdf-generator-project/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── hamzaelkhatib/
│   │   │           └── pdfgenerator/
│   │   │               ├── controller/
│   │   │               │   └── PdfController.java
│   │   │               ├── config/
│   │   │               │   ├── ConfigProperties.java
│   │   │               │   └── AsyncConfig.java
│   │   │               ├── service/
│   │   │               │   ├── PdfGeneratorService.java
│   │   │               │   ├── FileCleanupService.java
│   │   │               │   └── PdfTaskManager.java
│   │   │               ├── utils/
│   │   │               │   ├── PdfUtils.java
│   │   │               │   └── CookieHelper.java
│   │   │               └── PdfGeneratorApplication.java
│   │   └── resources/
│   │       └── application.yml
└── pom.xml
```

### Dependencies

| Name | Dependency | Maintainer(s) | License | Version | Commercial Use | Uses in Project |
|------|------------|---------------|----------|----------|----------------|-----------------|
| Spring Boot Starter Web | `org.springframework.boot:spring-boot-starter-web` | Pivotal Software | Apache License 2.0 | 3.3.5 | Yes | Web framework for RESTful APIs |
| Lombok | `org.projectlombok:lombok` | Project Lombok | MIT License | - | Yes | Reduces boilerplate code |
| Spring Boot Starter Test | `org.springframework.boot:spring-boot-starter-test` | Pivotal Software | Apache License 2.0 | 3.3.5 | Yes | Testing framework |
| Playwright | `com.microsoft.playwright:playwright` | Microsoft | MIT License | 1.28.0 | Yes | PDF generation from HTML |
| PDFBox | `org.apache.pdfbox:pdfbox` | Apache Software Foundation | Apache License 2.0 | 2.0.27 | Yes | PDF manipulation and merging |
| SLF4J API | `org.slf4j:slf4j-api` | SLF4J Team | MIT License | 2.0.7 | Yes | Logging abstraction |

## 2. Configuration

### YAML Configuration Reference

#### PDF Generation Settings
| Parameter | Description | Default Value | Notes |
|-----------|-------------|---------------|-------|
| `pdf.storage.path` | Directory path for PDF storage | `/data/generated_pdfs` | Absolute path to store generated PDFs |
| `pdf.pages-to-remove` | Number of pages to remove from end | 2 | Removes specified pages from each chunk |

#### Cleanup Configuration
| Parameter | Description | Default Value | Notes |
|-----------|-------------|---------------|-------|
| `pdf.cleanup.file-retention-duration-ms` | File retention duration | 1800000 | 30 minutes in milliseconds |
| `pdf.cleanup.cleanup-interval-ms` | Cleanup check interval | 600000 | 10 minutes in milliseconds |

#### Ranking Service Configuration
| Parameter | Description | Default Value | Notes |
|-----------|-------------|---------------|-------|
| `pdf.ranking.root` | Base URL for PDF export | `https://comercial.central.inditex.grp/cdarkspa-st4/web/export` | Export service URL |
| `pdf.ranking.domain` | Domain for cookie settings | `comercial.central.inditex.grp` | Used for cookie configuration |
| `pdf.ranking.environment` | Environment identifier | `local` | Environment setting |
| `pdf.ranking.articles-per-pdf` | Articles per PDF chunk | 200 | Number of articles in each PDF section |
| `pdf.ranking.batch-size` | Concurrent PDF generations | 2 | Number of PDFs generated simultaneously |

#### Execution Configuration
| Parameter | Description | Default Value | Notes |
|-----------|-------------|---------------|-------|
| `pdf.executor.pool-size` | Thread pool size | 1 | Number of concurrent threads |

#### Server Configuration
| Parameter | Description | Default Value | Notes |
|-----------|-------------|---------------|-------|
| `cors.allowed-origin` | Allowed CORS origin | `https://comercial.central.inditex.grp` | CORS configuration |
| `server.port` | Server port | 8080 | Application port |
| `spring.application.name` | Application name | pdfgenerator | Service identifier |

### Example Configuration
```yaml
pdf:
    storage:
        path: /data/generated_pdfs
    cleanup:
        file-retention-duration-ms: 1800000  # 30 minutes
        cleanup-interval-ms: 600000          # 10 minutes
    ranking:
        root: "https://comercial.central.inditex.grp/cdarkspa-st4/web/export"
        domain: "comercial.central.inditex.grp"
        environment: "local"
        articles-per-pdf: 200
        batch-size: 2
    executor:
        pool-size: 1
    pages-to-remove: 2

cors:
    allowed-origin: "https://comercial.central.inditex.grp"

server:
    port: 8080

spring:
    application:
        name: pdfgenerator
```

## 3. Setup Instructions

### Prerequisites
- Java 17
- Maven
- Chromium (installed automatically)

### Installation Steps

1. Clone the repository
```bash
cd pdfgenerator
```

2. Install Playwright dependencies
```bash
# Install Playwright
mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install"

# Install browser
mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium"
```

3. Build the project
```bash
mvn clean package
```

4. Run the application
```bash
java -jar target/pdfgenerator-0.0.1-SNAPSHOT.jar
```

## 4. API Documentation

### Endpoints

#### 1. Generate PDF
```http
GET /api/v1/generateRankingPdf
```

**Query Parameters:**
- `dataUrl` (required): Source data URL
- `numberOfArticles` (required): Number of articles to process

**Headers:**
- `Cookie` (required): Authentication cookies

**Response:**
```json
{
    "taskId": "/api/v1/checkPdfStatus?taskId=uuid"
}
```

#### 2. Check Status
```http
GET /api/v1/checkPdfStatus
```

**Query Parameters:**
- `taskId` (required): Task UUID

**Responses:**
- `200 OK` with PDF file: Generation complete
- `204 No Content`: In progress
- `200 OK` with message: File not found

## 5. Process Flow

### Main Process Flow

![Main Process Flow](https://mermaid.ink/img/pako:eNqdVs1u20YQfpUFC0MtIAMU7cQODwUkSnQL2KmgnxwaFsaGHEoLU7vE7jKJYQvooaceChQtECCXIIce-1J9gj5C94daijajNpUO5Mx-M_PN7Oxw77yUZeCF3tHRHaFEhuguoQj15Bo20AtRL8P8ptdvdC8wJ_hVAaJXQ9VCyckG89uIFYxrmy-Cif5bs2Z9AW9lg8nz_CFgxHgGvIGcRb7_7MyhCkKhWYzPR0HwzC0KSBnNWix8_-nA9x1CApfkAWAQP4l6en2b0O326CihCV1xXK7RYmTNhMRcfvky8f7-8P5HNNdS4v3wFTo-_hqpiJKzogBu1n_7A02zPHJKhbM-Gpw2u0-8C6DAsQS0XH47Trx7VALNCF0pNxEHvTC1ChSTApyfGmVir2oXQ3FLUxP-95-cUtEwemdZ51K9ssktZ5fXF5Pn1uydFlFNiTDqjGz6twXs8CgnRRHWu9sXKqcbCOuNqMXjNyST6zAo3zY-dqyWvBAv95OfXQoVzKQjyoLIEZbpWiHmWkCESobSdUVvBGI5CnzfMVN10DvVymk0XETfXE9n30XKw1_vPyLjDU05S0EIUzYUICwRRpJsoCPLxsMnEo1Pz4b-gUTbXAaKh-UwaAV7EHBQBxtMTsZPzl0w3x-eT87bVqV2GemKGJeqcMeoDJwuaIUxNdoJn-IYOI7BIY7BZ3A8cXxOHMdTpzv9Lxzruui-sOG7QKXdWOU5ZhwBVjmYZgl3LVUW-PYNJ6u1NH3-y59o6jQtFg3Q2mX5fpdOx3EbneUGxmHDXsMUr0BzmBkJXWIhVZcZ7cFuvZrMLiZ2avyqDjnFxV6ndvSmwXe3pe_HsX-oLTfAV2DKr5leaUln5c5eBgWomeFG0NjI3SNI_1p4OwgLwLQqG1sbrasCrblljOvhYpd3k8btvTmPdm3vfOplU5JHhZ0vhovl3Fb2Zz2vZSUUHUhvugtr8f_zwKfa753ubfWsgyXe1kz5uj5utl9zECWjAky3yIpTNdLax8G4M8YR25S6kNk9EsBfa5u5fj7qxsbkOZMoZhVVJpTJXL81ka5UY6me7NoQ21lmF7WvnTotsBBjyNVu57gqZHeF7Bf6UYX6qf7Ghvob_8BbfWoPzZPP8KYShdqV_dj_26F45GrPYbs1-3unZsd6H2zuBX1bfk3D63vKYoNJpi5T5mKUeObClHihetXXqMRTlwyFw5VkcxXCCyWvoO9xVq3WXpjjQiipKjPFYUyw6ueN05aYfs9YI0NGJONX9u5mrnDbfwCWmw9E)

#### Process Steps
1. **Initial Setup**
    - Application receives request to process articles
    - Articles divided into chunks based on configuration

2. **Chunk Generation**
    - Each chunk processed in parallel
    - PDF generated and temporarily saved
    - Processing time varies with system performance

3. **Finalization**
    - Generated PDFs merged into final output
    - Final PDF compressed
    - Temporary files cleaned up

### Cleanup Process

![Cleanup Process](https://mermaid.ink/img/pako:eNplksFuwjAMhl_F8hleoNqQgLYDxHZYkSat6SFKTRvRJlWaTEMt776QIsa0nPLn_2wndgYUuiSMsDK8q-EQMwV-LfPMcmNh3RBXroOMzJcUVMB8voBVzjATNZWuoRIOvD8xLKa4VQDWw172FlLZUA9SQWa14RVdJmZ9ZcbEGG1GSPK9riCI4tHOnBDU9yPEuU9jyUzZbkwcyqQDw1QbSLiog83wViINORguK4IFGLKkrNSK4QgveUyNPwgBxX_86fkvv8mzk-we6ZdQfBsu_k69a-zN2ARjl39waeHoL_ZG376FZ3EP3U7EJJJHsZs6izNsybRcln4kw9ViaGtq_dMivy258b1m6uI57qzOzkpgZI2jGRrtqhqjI296r1xXckux5H6u7f204-pT619NpfSjeZ1-QPgIlx-abaOW)


