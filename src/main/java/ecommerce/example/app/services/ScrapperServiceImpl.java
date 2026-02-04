package ecommerce.example.app.services;

import ecommerce.example.app.models.dtos.DetectionResult;
import ecommerce.example.app.utils.enums.Platform;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
@Slf4j
public class ScrapperServiceImpl implements ScrapperService {

    private final HttpClient httpClient;

    private final Executor executor;

    private static final int MAX_CONCURRENCY = 10;

    public ScrapperServiceImpl(HttpClient httpClient, @Qualifier("scrapingExecutor") Executor executor) {
        this.httpClient = httpClient;
        this.executor = executor;
    }

    @Override
    public List<DetectionResult> detectFromUrls(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            log.info("No URLs provided for detection");
            return List.of();
        }

        log.info("Starting platform detection for {} URL(s) with max concurrency {}",
                urls.size(), MAX_CONCURRENCY);

        List<CompletableFuture<DetectionResult>> futures = new ArrayList<>(urls.size());

        for (String url : urls) {
            futures.add(CompletableFuture
                    .supplyAsync(() -> detectOne(url), executor)
                    // Safety net: detectOne already catches fetch errors, but keep this to avoid failing the whole batch
                    .exceptionally(ex -> {
                        log.warn("Unexpected failure for '{}': {}", url, ex.toString());
                        return DetectionResult.error(url, -1, "Unexpected error: " + ex.getClass().getSimpleName());
                    })
            );
        }

        // Wait for all to finish (no partial hangs beyond your per-request timeout)
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Keep result order same as input order
        List<DetectionResult> results = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        long okCount = results.stream().filter(DetectionResult::isOk).count();
        log.info("Finished platform detection: {} succeeded, {} failed", okCount, results.size() - okCount);

        return results;
    }

    @Override
    public String detectFromUrlsCsv(List<String> urls) {
        List<DetectionResult> results = detectFromUrls(urls);

        StringBuilder sb = new StringBuilder();
        sb.append("website,platforms\n");

        for (DetectionResult r : results) {
            String website = csvEscape(r.getUrl());

            String platforms = "";
            if (r.isOk() && r.getPlatforms() != null && !r.getPlatforms().isEmpty()) {
                platforms = r.getPlatforms().stream()
                        .map(Enum::name)
                        .sorted()
                        .collect(java.util.stream.Collectors.joining("|"));
            }

            sb.append(website).append(",").append(csvEscape(platforms)).append("\n");
        }

        return sb.toString();
    }

    private String csvEscape(String s) {
        if (s == null) return "";
        boolean mustQuote = s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r");
        String escaped = s.replace("\"", "\"\"");
        return mustQuote ? ("\"" + escaped + "\"") : escaped;
    }

    private DetectionResult detectOne(String url) {
        if (url == null || url.trim().isEmpty()) {
            log.warn("Skipping empty URL input");
            return DetectionResult.error(url, -1, "Empty URL");
        }

        String normalized = normalizeUrl(url);
        if (!Objects.equals(url, normalized)) {
            log.debug("Normalized URL: '{}' -> '{}'", url, normalized);
        }

        log.debug("Fetching HTML for {}", normalized);
        FetchResult fetch = fetchHtml(normalized);

        if (!fetch.ok) {
            log.warn("Fetch failed for '{}': {}", normalized, fetch.errorMessage);
            return DetectionResult.error(url, fetch.statusCode, fetch.errorMessage);
        }

        String html = fetch.html == null ? "" : fetch.html;
        log.debug("Fetched {} chars from '{}' (HTTP {})", html.length(), normalized, fetch.statusCode);

        DetectionResult result = detectFromHtml(url, html);
        if (result.getPlatforms() == null || result.getPlatforms().isEmpty()) {
            log.debug("No platform detected for '{}'", url);
        } else {
            log.debug("Detected {} for '{}'", result.getPlatforms(), url);
        }

        return result;
    }

    private FetchResult fetchHtml(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(12))
                    .header("User-Agent", "Mozilla/5.0 (PlatformDetectorBot/1.0)")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .GET()
                    .build();

            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            int code = res.statusCode();
            if (code < 200 || code >= 400) {
                return FetchResult.fail(code, "HTTP " + code);
            }

            return FetchResult.ok(code, res.body());
        } catch (Exception e) {
            return FetchResult.fail(-1, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private DetectionResult detectFromHtml(String url, String html) {
        String lower = (html == null ? "" : html).toLowerCase(Locale.ROOT);

        EnumSet<Platform> found = EnumSet.noneOf(Platform.class);
        Map<Platform, List<String>> evidence = new EnumMap<>(Platform.class);

        // GoMag
        if (contains(lower, "gomag")) {
            hit(found, evidence, Platform.GOMAG, "Found substring: gomag");
        }

        // MerchantPro
        if (contains(lower, "merchantpro") ||
                contains(lower, "merchant pro") ||
                contains(lower, "powered by merchant") ||
                contains(lower, "made with merchant")) {

            hit(found, evidence, Platform.MERCHANTPRO, "MerchantPro footer text detected");
        }

        // Shopify (hardened)
        if (
                contains(lower, "cdn.shopify.com") ||
                        contains(lower, "myshopify.com") ||
                        contains(lower, "shopify-checkout") ||
                        contains(lower, "shopify-pay") ||
                        contains(lower, "shopify-features") ||
                        contains(lower, "shopify.buy") ||
                        contains(lower, "shopify.theme") ||
                        contains(lower, "shopify.routes") ||
                        contains(lower, "window.shopify") ||
                        contains(lower, "shopifyanalytics") ||
                        contains(lower, "shopifycdn") ||
                        contains(lower, "shopify-section")
        ) {
            hit(found, evidence, Platform.SHOPIFY, "Shopify technical markers detected");
        }

        // WooCommerce (hardened)
        if (
                contains(lower, "wp-content/plugins/woocommerce") ||
                        contains(lower, "wp-content/uploads/woocommerce") ||
                        contains(lower, "woocommerce-no-js") ||
                        contains(lower, "wc-ajax") ||
                        contains(lower, "woocommerce_params") ||
                        contains(lower, "wc_add_to_cart_params") ||
                        contains(lower, "woocommerce-cart") ||
                        contains(lower, "woocommerce-checkout") ||
                        contains(lower, "woocommerce-product-gallery")
        ) {
            hit(found, evidence, Platform.WOOCOMMERCE, "WooCommerce technical markers detected");
        }

        // MAGENTO
        int magentoScore = 0;

        boolean mExclusive =
                contains(lower, "data-mage-init") ||
                        contains(lower, "text/x-magento-init") ||
                        contains(lower, "/static/version") ||
                        contains(lower, "/static/frontend/") ||
                        contains(lower, "/static/adminhtml/") ||
                        contains(lower, "magento_") ||               // e.g., Magento_Catalog, magento_theme
                        contains(lower, "mage-cache-sessid") ||
                        contains(lower, "name=\"form_key\"") ||
                        contains(lower, "form_key");

        if (contains(lower, "data-mage-init")) magentoScore++;
        if (contains(lower, "text/x-magento-init")) magentoScore++;
        if (contains(lower, "/static/version")) magentoScore++;
        if (contains(lower, "/static/frontend/")) magentoScore++;
        if (contains(lower, "/static/adminhtml/")) magentoScore++;
        if (contains(lower, "magento_")) magentoScore++;
        if (contains(lower, "mage-cache-sessid")) magentoScore++;
        if (contains(lower, "name=\"form_key\"") || contains(lower, "form_key")) magentoScore++;

        // NOTE: requirejs alone is NOT reliable; only count it as a weak hint
        if (contains(lower, "requirejs/require")) magentoScore += 0; // intentionally ignored

        // Final decision:
        // - must have at least one Magento-exclusive marker
        // - must have >= 2 total strong markers (score)
        if (mExclusive && magentoScore >= 2) {
            hit(found, evidence, Platform.MAGENTO,
                    "Magento detected (strict markers: score=" + magentoScore + ")");
        }

        // OpenCart (hardened)
        if (
                contains(lower, "index.php?route=") ||
                        contains(lower, "route=common/home") ||
                        contains(lower, "route=product/product") ||
                        contains(lower, "route=checkout/cart") ||
                        contains(lower, "catalog/view/theme/") ||
                        contains(lower, "catalog/view/javascript/")
        ) {
            hit(found, evidence, Platform.OPENCART, "OpenCart route/catalog markers detected");
        }

        // PrestaShop (hardened)
        // PrestaShop (STRICT)
        if (
                contains(lower, "data-prestashop") ||
                        contains(lower, "var prestashop =") ||
                        contains(lower, "prestashop.emit") ||
                        contains(lower, "prestashop-static") ||
                        contains(lower, "/modules/ps_") ||      // very strong PS signal
                        contains(lower, "id=\"prestashop\"")    // sometimes appears in PS templates
        ) {
            hit(found, evidence, Platform.PRESTASHOP, "PrestaShop specific markers detected");
        }

        return DetectionResult.ok(url, found, evidence);
    }

    private void hit(Set<Platform> found,
                     Map<Platform, List<String>> evidence,
                     Platform p,
                     String why) {

        found.add(p);
        evidence.computeIfAbsent(p, k -> new ArrayList<>()).add(why);
    }

    private boolean contains(String htmlLower, String needle) {
        return htmlLower.contains(needle.toLowerCase(Locale.ROOT));
    }

    private static String normalizeUrl(String url) {
        String u = url == null ? "" : url.trim();
        if (u.isEmpty()) return u;
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            u = "https://" + u;
        }
        return u;
    }
}
