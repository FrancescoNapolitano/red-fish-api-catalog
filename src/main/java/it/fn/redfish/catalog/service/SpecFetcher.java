package it.fn.redfish.catalog.service;

import it.fn.redfish.catalog.support.I18n;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.stereotype.Component;

import it.fn.redfish.catalog.config.CatalogProperties;
import it.fn.redfish.catalog.support.BusinessException;
import it.fn.redfish.catalog.support.Text;

@Component
public class SpecFetcher {

    public record Fetched(String content, String contentType, String fileName, long size) {
    }

    private final CatalogProperties properties;
    private final HttpClient client;

    public SpecFetcher(CatalogProperties properties) {
        this.properties = properties;
        this.client = HttpClient.newBuilder()
                .connectTimeout(properties.getImportFetchTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public Fetched fetch(String rawUrl, long maxBytes) {
        URI uri = validate(rawUrl);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(properties.getImportFetchTimeout())
                .header("Accept", "application/json, application/yaml, text/yaml, text/plain, */*")
                .header("User-Agent", "red-fish-api-catalog")
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                throw new BusinessException(I18n.text("message.the.remote.server.returned.http.status") + response.statusCode());
            }
            byte[] body = readAtMost(response.body(), maxBytes);
            String contentType = response.headers().firstValue("content-type").orElse(null);
            String content = Text.stripBom(new String(body, StandardCharsets.UTF_8));
            return new Fetched(content, contentType, fileNameOf(uri), body.length);
        } catch (IOException e) {
            throw new BusinessException(I18n.text("message.cannot.download.the.specification") + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(I18n.text("message.download.interrupted"), e);
        }
    }

    private URI validate(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new BusinessException(I18n.text("message.the.specification.url.is.required"));
        }
        URI uri;
        try {
            uri = new URI(rawUrl.trim());
        } catch (URISyntaxException e) {
            throw new BusinessException(I18n.text("message.invalid.url") + rawUrl);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new BusinessException(I18n.text("message.only.http.and.https.urls.are.allowed"));
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new BusinessException(I18n.text("message.url.has.no.host.2") + rawUrl);
        }
        return uri;
    }

    private byte[] readAtMost(InputStream in, long maxBytes) throws IOException {
        try (in) {
            byte[] buffer = new byte[8192];
            var out = new java.io.ByteArrayOutputStream();
            long total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new BusinessException(I18n.text("message.the.remote.specification.exceeds.the.limit.of")
                            + (maxBytes / 1024) + " KB");
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    private String fileNameOf(URI uri) {
        String path = uri.getPath();
        if (path == null || path.isBlank() || path.endsWith("/")) {
            return "spec-remoto";
        }
        return path.substring(path.lastIndexOf('/') + 1);
    }

    public Duration timeout() {
        return properties.getImportFetchTimeout();
    }
}
