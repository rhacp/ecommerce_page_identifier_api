package ecommerce.example.app.controllers;

import ecommerce.example.app.models.dtos.BatchDetectRequest;
import ecommerce.example.app.models.dtos.DetectionResult;
import ecommerce.example.app.services.ScrapperService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/scrape")
public class EcommerceController {

    private final ScrapperService scrapperService;

    public EcommerceController(ScrapperService scrapperService) {
        this.scrapperService = scrapperService;
    }

    @PostMapping
    public ResponseEntity<List<DetectionResult>> detectPlatforms(@RequestBody BatchDetectRequest request) {
        return ResponseEntity.ok(scrapperService.detectFromUrls(request.urls()));
    }


    @PostMapping(value = "/csv", produces = "text/csv")
    public ResponseEntity<String> detectCsv(@RequestBody BatchDetectRequest request) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=utf-8")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"detections.csv\"")
                .body(scrapperService.detectFromUrlsCsv(request.urls()));
    }
}
