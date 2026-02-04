package ecommerce.example.app.models.dtos;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import ecommerce.example.app.utils.enums.Platform;
import lombok.Data;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@JsonPropertyOrder({ "url", "ok", "statusCode", "error", "platforms", "evidence" })
@Data
public final class DetectionResult {

    private String url;
    private boolean ok;
    private int statusCode;
    private String error;
    private Set<Platform> platforms;
    private Map<Platform, List<String>> evidence;

    private DetectionResult(String url, boolean ok, int statusCode, String error,
                            Set<Platform> platforms,
                            Map<Platform, List<String>> evidence) {
        this.url = url;
        this.ok = ok;
        this.statusCode = statusCode;
        this.error = error;
        this.platforms = platforms == null ? Set.of() : Collections.unmodifiableSet(platforms);
        this.evidence = evidence == null ? Map.of() : Collections.unmodifiableMap(evidence);
    }

    public static DetectionResult ok(String url, Set<Platform> platforms, Map<Platform, List<String>> evidence) {
        return new DetectionResult(url, true, 200, null, platforms, evidence);
    }

    public static DetectionResult error(String url, int statusCode, String error) {
        return new DetectionResult(url, false, statusCode, error, Set.of(), Map.of());
    }
}
