package it.fn.redfish.catalog.service;

import it.fn.redfish.catalog.support.I18n;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import it.fn.redfish.catalog.spec.EndpointView;

@Component
public class CurlBuilder {

    public String fromEndpoint(EndpointView view, String baseUrl) {
        var endpoint = view.getEndpoint();
        if (ApiUrls.resolve(view, baseUrl).isBlank()) {
            return "";
        }
        if (endpoint.isGrpc()) {
            return grpcHint(view, baseUrl);
        }

        String path = endpoint.getPath();
        for (EndpointView.ParamView param : view.getPathParams()) {
            path = path.replace("{" + param.name() + "}", placeholder(param));
        }

        Map<String, String> query = new LinkedHashMap<>();
        view.getQueryParams().stream()
                .filter(EndpointView.ParamView::required)
                .forEach(p -> query.put(p.name(), placeholder(p)));

        Map<String, String> headers = new LinkedHashMap<>();
        view.getHeaderParams().stream()
                .filter(EndpointView.ParamView::required)
                .forEach(p -> headers.put(p.name(), placeholder(p)));

        String contentType = view.defaultContentType();
        String body = view.defaultBodySample();
        if (contentType != null && body != null) {
            headers.put("Content-Type", contentType);
        }
        if (endpoint.getProduces() != null && !endpoint.getProduces().isBlank()) {
            headers.put("Accept", endpoint.getProduces().split(",")[0].trim());
        }
        if (!view.getSecurity().isEmpty()) {
            EndpointView.SecurityView security = view.getSecurity().get(0);
            headers.putAll(authHeader(security));
        }

        return build(endpoint.getHttpMethod(), ApiUrls.resolve(view, baseUrl), path, query, headers, body);
    }

    public String build(String method, String baseUrl, String path, Map<String, String> query,
                        Map<String, String> headers, String body) {
        StringBuilder sb = new StringBuilder("curl -i -X ").append(method == null ? "GET" : method.toUpperCase());

        StringBuilder url = new StringBuilder(trimTrailingSlash(baseUrl == null ? "" : baseUrl));
        url.append(path == null || path.isEmpty() ? "" : (path.startsWith("/") ? path : "/" + path));
        if (query != null && !query.isEmpty()) {
            url.append(url.indexOf("?") >= 0 ? '&' : '?');
            List<String> pairs = new ArrayList<>();
            query.forEach((k, v) -> pairs.add(encode(k) + "=" + encode(v == null ? "" : v)));
            url.append(String.join("&", pairs));
        }
        sb.append(" \\\n  '").append(escapeSingleQuotes(url.toString())).append('\'');

        if (headers != null) {
            headers.forEach((k, v) -> {
                if (k != null && !k.isBlank()) {
                    sb.append(" \\\n  -H '").append(escapeSingleQuotes(k)).append(": ")
                            .append(escapeSingleQuotes(v == null ? "" : v)).append('\'');
                }
            });
        }
        if (body != null && !body.isBlank()) {
            sb.append(" \\\n  -d '").append(escapeSingleQuotes(body)).append('\'');
        }
        return sb.toString();
    }

    private String grpcHint(EndpointView view, String baseUrl) {
        var endpoint = view.getEndpoint();
        String host = hostOf(ApiUrls.resolve(view, baseUrl));
        return I18n.text("message.grpc.hint")
                + "grpcurl -plaintext \\\n"
                + "  -d '" + escapeSingleQuotes(orEmptyJson(view.defaultBodySample())) + "' \\\n"
                + "  " + host + " \\\n"
                + "  " + endpoint.getGrpcService() + "/" + endpoint.getGrpcMethod();
    }

    private String orEmptyJson(String body) {
        return body == null || body.isBlank() ? "{}" : body;
    }

    private String hostOf(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "localhost:50051";
        }
        String value = baseUrl.replaceFirst("^https?://", "");
        int slash = value.indexOf('/');
        return slash < 0 ? value : value.substring(0, slash);
    }

    private Map<String, String> authHeader(EndpointView.SecurityView security) {
        if (security == null || security.type() == null) {
            return Map.of();
        }
        return switch (security.type()) {
            case "http" -> "basic".equalsIgnoreCase(security.scheme())
                    ? Map.of("Authorization", "Basic <base64(user:password)>")
                    : Map.of("Authorization", "Bearer <token>");
            case "oauth2", "openIdConnect" -> Map.of("Authorization", "Bearer <access_token>");
            case "apiKey" -> "header".equalsIgnoreCase(security.location())
                    ? Map.of(security.name(), "<api-key>")
                    : Map.of();
            default -> Map.of();
        };
    }

    private String placeholder(EndpointView.ParamView param) {
        if (param.example() != null && !param.example().isBlank()) {
            return param.example();
        }
        if (param.enumValues() != null && !param.enumValues().isEmpty()) {
            return param.enumValues().get(0);
        }
        return "<" + param.name() + ">";
    }

    private String trimTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String escapeSingleQuotes(String value) {
        return value == null ? "" : value.replace("'", "'\\''");
    }
}
