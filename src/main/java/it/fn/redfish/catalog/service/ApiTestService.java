package it.fn.redfish.catalog.service;

import it.fn.redfish.catalog.support.I18n;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import it.fn.redfish.catalog.support.BusinessException;

@Service
public class ApiTestService {

    private static final Set<String> SUPPORTED_METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");
    private static final int MAX_RESPONSE_CHARS = 200_000;

    private static final Set<String> BLOCKED_HEADERS = Set.of(
            "host", "content-length", "connection", "upgrade", "transfer-encoding", "expect");

    public record TestRequest(String method, String url, Map<String, String> headers, String body) {
    }

    public record TestResponse(int status, String statusText, long durationMillis, String body, String contentType,
                               List<Map.Entry<String, String>> headers, long sizeBytes, boolean truncated,
                               String error, String finalUrl) {

        public boolean isSuccess() {
            return status >= 200 && status < 300;
        }

        public boolean isFailed() {
            return error != null;
        }
    }

    private final SettingsService settings;
    private final AuditService audit;

    public ApiTestService(SettingsService settings, AuditService audit) {
        this.settings = settings;
        this.audit = audit;
    }

    public TestResponse execute(TestRequest request) {
        String method = normalizeMethod(request.method());
        URI uri = parseUri(request.url());
        Duration timeout = settings.apiTestTimeout();

        HttpRequest httpRequest = buildRequest(request, method, uri, timeout);

        long started = System.nanoTime();

        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()) {

            HttpResponse<String> response = client.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long elapsed = Duration.ofNanos(System.nanoTime() - started).toMillis();

            String fullBody = response.body() == null ? "" : response.body();
            boolean truncated = fullBody.length() > MAX_RESPONSE_CHARS;
            String body = truncated ? fullBody.substring(0, MAX_RESPONSE_CHARS) : fullBody;

            audit.record("API_TEST", "Endpoint", null,
                    method + " " + uri + " → " + response.statusCode() + I18n.text("message.in") + elapsed + " ms");

            return new TestResponse(response.statusCode(), reasonPhrase(response.statusCode()), elapsed, body,
                    response.headers().firstValue("content-type").orElse(null),
                    flatten(response.headers().map()),
                    fullBody.getBytes(StandardCharsets.UTF_8).length,
                    truncated, null, response.uri().toString());

        } catch (IOException e) {
            long elapsed = Duration.ofNanos(System.nanoTime() - started).toMillis();
            audit.recordFailure("API_TEST", "Endpoint", null, method + " " + uri + " → " + e.getMessage());
            return new TestResponse(0, null, elapsed, null, null, List.of(), 0, false,
                    describe(e), uri.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new TestResponse(0, null, 0, null, null, List.of(), 0, false, I18n.text("message.request.interrupted"),
                    uri.toString());
        }
    }

    private HttpRequest buildRequest(TestRequest request, String method, URI uri, Duration timeout) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(timeout);

        boolean hasBody = request.body() != null && !request.body().isBlank()
                && !"GET".equals(method) && !"DELETE".equals(method);
        builder.method(method, hasBody
                ? HttpRequest.BodyPublishers.ofString(request.body(), StandardCharsets.UTF_8)
                : HttpRequest.BodyPublishers.noBody());

        boolean hasAccept = false;
        if (request.headers() != null) {
            for (Map.Entry<String, String> header : request.headers().entrySet()) {
                String name = header.getKey() == null ? "" : header.getKey().trim();
                if (name.isBlank() || BLOCKED_HEADERS.contains(name.toLowerCase())) {
                    continue;
                }
                String value = header.getValue() == null ? "" : header.getValue().trim();
                try {
                    builder.header(name, value);
                } catch (IllegalArgumentException e) {
                    throw new BusinessException(I18n.text("message.invalid.header") + name);
                }
                hasAccept |= "accept".equalsIgnoreCase(name);
            }
        }
        if (!hasAccept) {
            builder.header("Accept", "*/*");
        }
        return builder.build();
    }

    private String describe(IOException e) {
        if (e instanceof java.net.http.HttpTimeoutException) {
            return I18n.text("message.timeout.after") + settings.apiTestTimeout().toSeconds() + I18n.text("message.seconds");
        }
        if (e instanceof java.net.UnknownHostException) {
            return I18n.text("message.cannot.resolve.host") + e.getMessage();
        }
        if (e instanceof java.net.ConnectException) {
            return I18n.text("message.connection.refused") + e.getMessage();
        }
        if (e instanceof javax.net.ssl.SSLException) {
            return I18n.text("message.tls.error") + e.getMessage();
        }
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }

    private String normalizeMethod(String method) {
        String normalized = method == null ? "GET" : method.trim().toUpperCase();
        if (!SUPPORTED_METHODS.contains(normalized)) {
            throw new BusinessException(I18n.text("message.unsupported.method") + normalized
                    + I18n.text("message.allowed") + String.join(", ", SUPPORTED_METHODS) + ")");
        }
        return normalized;
    }

    private URI parseUri(String url) {
        if (url == null || url.isBlank()) {
            throw new BusinessException(I18n.text("message.the.request.url.is.required"));
        }
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException e) {
            throw new BusinessException(I18n.text("message.invalid.url") + url);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new BusinessException(I18n.text("message.only.http.and.https.requests.are.allowed"));
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new BusinessException(I18n.text("message.url.has.no.host"));
        }
        return uri;
    }

    private List<Map.Entry<String, String>> flatten(Map<String, List<String>> headers) {
        List<Map.Entry<String, String>> flat = new ArrayList<>();
        new java.util.TreeMap<>(headers).forEach((name, values) ->
                values.forEach(value -> flat.add(Map.entry(name, value))));
        return flat;
    }

    private String reasonPhrase(int status) {
        var resolved = org.springframework.http.HttpStatus.resolve(status);
        return resolved == null ? "" : resolved.getReasonPhrase();
    }

    public static Map<String, String> parseHeaderLines(String text) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (text == null || text.isBlank()) {
            return headers;
        }
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int colon = trimmed.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            headers.put(trimmed.substring(0, colon).trim(), trimmed.substring(colon + 1).trim());
        }
        return headers;
    }
}
