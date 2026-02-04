# E-commerce Platform Detector API

Spring Boot REST API that detects which e-commerce platform a website uses (Shopify, Magento, WooCommerce, PrestaShop, OpenCart, GoMag, MerchantPro).

---

## Run with Docker

1) Download the zip and extract it.

2) Open a terminal in the project root folder (the folder that contains Dockerfile, pom.xml, src).

3) Build the Docker image:
   docker build -t platform-detector .

4) Run the container:
   docker run -p 8080:8080 platform-detector

API will be available at:
http://localhost:8080

---

## Test the API

Import the Postman collection located in the project root:
postman-collection.json

---

## Endpoints

1) Detect (JSON)
   POST /api/scrape
   Body example:
   { "urls": ["shopify.com", "gomag.ro"] }

2) Detect (CSV)
   POST /api/scrape/csv
   Returns a CSV string with header:
   website,platforms

---

## Local run (without Docker)

Requirements: Java 21, Maven

Run:
mvn spring-boot:run

---
