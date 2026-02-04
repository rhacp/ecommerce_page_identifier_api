package ecommerce.example.app.services;

public final class FetchResult {
    final boolean ok;
    final int statusCode;
    final String html;
    final String errorMessage;

    private FetchResult(boolean ok, int statusCode, String html, String errorMessage) {
        this.ok = ok;
        this.statusCode = statusCode;
        this.html = html;
        this.errorMessage = errorMessage;
    }

    static FetchResult ok(int code, String html) {
        return new FetchResult(true, code, html, null);
    }

    static FetchResult fail(int code, String err) {
        return new FetchResult(false, code, null, err);
    }
}
