package it.fn.redfish.catalog.service;

import it.fn.redfish.catalog.spec.EndpointView;

public final class ApiUrls {
    private ApiUrls() {
    }

    public static String resolve(EndpointView view, String environmentUrl) {
        if (environmentUrl != null && !environmentUrl.isBlank()) {
            return trim(environmentUrl);
        }
        return view.getServers().stream().map(EndpointView.ServerView::url)
                .filter(url -> url != null && (url.startsWith("https://") || url.startsWith("http://")))
                .filter(url -> !url.contains("{"))
                .findFirst().map(ApiUrls::trim).orElse("");
    }

    public static String endpoint(String baseUrl, String path) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "";
        }
        return trim(baseUrl) + (path == null || path.isEmpty() ? "" : (path.startsWith("/") ? path : "/" + path));
    }

    private static String trim(String url) {
        return url.trim().replaceAll("/+$", "");
    }
}
