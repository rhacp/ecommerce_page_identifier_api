package ecommerce.example.app.services;

import ecommerce.example.app.models.dtos.DetectionResult;

import java.util.List;

public interface ScrapperService {

    List<DetectionResult> detectFromUrls(List<String> urls);

    String detectFromUrlsCsv(List<String> urls);
}
