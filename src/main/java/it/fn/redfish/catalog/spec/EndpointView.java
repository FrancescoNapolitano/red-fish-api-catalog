package it.fn.redfish.catalog.spec;

import java.util.ArrayList;
import java.util.List;

import it.fn.redfish.catalog.domain.ApiEndpoint;

public class EndpointView {

    public record ParamView(String name, String in, boolean required, boolean deprecated, String description,
                            String typeLabel, String constraints, String example, List<String> enumValues,
                            SchemaView schema) {
    }

    public record ContentView(String mediaType, SchemaView schema, String sample) {
    }

    public record BodyView(boolean required, String description, List<ContentView> contents) {
    }

    public record ResponseView(String code, String description, List<ParamView> headers, List<ContentView> contents) {
    }

    public record SecurityView(String name, String type, String scheme, String location, String scopes,
                               String description) {
    }

    public record ServerView(String url, String description) {
    }

    private final ApiEndpoint endpoint;
    private final List<ParamView> pathParams = new ArrayList<>();
    private final List<ParamView> queryParams = new ArrayList<>();
    private final List<ParamView> headerParams = new ArrayList<>();
    private final List<ParamView> cookieParams = new ArrayList<>();
    private BodyView requestBody;
    private final List<ResponseView> responses = new ArrayList<>();
    private final List<SecurityView> security = new ArrayList<>();
    private final List<ServerView> servers = new ArrayList<>();
    private String curl;

    public EndpointView(ApiEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    public ApiEndpoint getEndpoint() {
        return endpoint;
    }

    public List<ParamView> getPathParams() {
        return pathParams;
    }

    public List<ParamView> getQueryParams() {
        return queryParams;
    }

    public List<ParamView> getHeaderParams() {
        return headerParams;
    }

    public List<ParamView> getCookieParams() {
        return cookieParams;
    }

    public BodyView getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(BodyView requestBody) {
        this.requestBody = requestBody;
    }

    public List<ResponseView> getResponses() {
        return responses;
    }

    public List<SecurityView> getSecurity() {
        return security;
    }

    public List<ServerView> getServers() {
        return servers;
    }

    public String getCurl() {
        return curl;
    }

    public void setCurl(String curl) {
        this.curl = curl;
    }

    public boolean hasParams() {
        return !pathParams.isEmpty() || !queryParams.isEmpty() || !headerParams.isEmpty() || !cookieParams.isEmpty();
    }

    public String defaultBodySample() {
        if (requestBody == null || requestBody.contents().isEmpty()) {
            return null;
        }
        return requestBody.contents().stream()
                .filter(c -> c.mediaType() != null && c.mediaType().contains("json"))
                .findFirst()
                .orElse(requestBody.contents().get(0))
                .sample();
    }

    public String defaultContentType() {
        if (requestBody == null || requestBody.contents().isEmpty()) {
            return null;
        }
        return requestBody.contents().stream()
                .map(ContentView::mediaType)
                .filter(m -> m != null && m.contains("json"))
                .findFirst()
                .orElse(requestBody.contents().get(0).mediaType());
    }
}
