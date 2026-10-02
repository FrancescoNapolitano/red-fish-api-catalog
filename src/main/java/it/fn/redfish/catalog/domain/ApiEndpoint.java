package it.fn.redfish.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "api_endpoint")
public class ApiEndpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "spec_version_id", nullable = false)
    private SpecVersion specVersion;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private ApiService service;

    @Column(name = "http_method", length = 12, nullable = false)
    private String httpMethod;

    @Column(name = "path", length = 1000, nullable = false)
    private String path;

    @Column(name = "operation_id", length = 255)
    private String operationId;

    @Column(name = "summary", length = 1000)
    private String summary;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "tags", length = 500)
    private String tags;

    @Column(name = "deprecated", nullable = false)
    private boolean deprecated;

    @Column(name = "parameters_json", columnDefinition = "text")
    private String parametersJson;

    @Column(name = "request_body_json", columnDefinition = "text")
    private String requestBodyJson;

    @Column(name = "responses_json", columnDefinition = "text")
    private String responsesJson;

    @Column(name = "security_json", columnDefinition = "text")
    private String securityJson;

    @Column(name = "servers_json", columnDefinition = "text")
    private String serversJson;

    @Column(name = "consumes", length = 500)
    private String consumes;

    @Column(name = "produces", length = 500)
    private String produces;

    @Column(name = "grpc_service", length = 255)
    private String grpcService;

    @Column(name = "grpc_method", length = 255)
    private String grpcMethod;

    @Column(name = "grpc_request_type", length = 255)
    private String grpcRequestType;

    @Column(name = "grpc_response_type", length = 255)
    private String grpcResponseType;

    @Column(name = "grpc_streaming", length = 30)
    private String grpcStreaming;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected ApiEndpoint() {
    }

    public ApiEndpoint(SpecVersion specVersion, String httpMethod, String path) {
        this.specVersion = specVersion;
        this.service = specVersion.getService();
        this.httpMethod = httpMethod;
        this.path = path;
    }

    public Long getId() {
        return id;
    }

    public SpecVersion getSpecVersion() {
        return specVersion;
    }

    public ApiService getService() {
        return service;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public boolean isDeprecated() {
        return deprecated;
    }

    public void setDeprecated(boolean deprecated) {
        this.deprecated = deprecated;
    }

    public String getParametersJson() {
        return parametersJson;
    }

    public void setParametersJson(String parametersJson) {
        this.parametersJson = parametersJson;
    }

    public String getRequestBodyJson() {
        return requestBodyJson;
    }

    public void setRequestBodyJson(String requestBodyJson) {
        this.requestBodyJson = requestBodyJson;
    }

    public String getResponsesJson() {
        return responsesJson;
    }

    public void setResponsesJson(String responsesJson) {
        this.responsesJson = responsesJson;
    }

    public String getSecurityJson() {
        return securityJson;
    }

    public void setSecurityJson(String securityJson) {
        this.securityJson = securityJson;
    }

    public String getServersJson() {
        return serversJson;
    }

    public void setServersJson(String serversJson) {
        this.serversJson = serversJson;
    }

    public String getConsumes() {
        return consumes;
    }

    public void setConsumes(String consumes) {
        this.consumes = consumes;
    }

    public String getProduces() {
        return produces;
    }

    public void setProduces(String produces) {
        this.produces = produces;
    }

    public String getGrpcService() {
        return grpcService;
    }

    public void setGrpcService(String grpcService) {
        this.grpcService = grpcService;
    }

    public String getGrpcMethod() {
        return grpcMethod;
    }

    public void setGrpcMethod(String grpcMethod) {
        this.grpcMethod = grpcMethod;
    }

    public String getGrpcRequestType() {
        return grpcRequestType;
    }

    public void setGrpcRequestType(String grpcRequestType) {
        this.grpcRequestType = grpcRequestType;
    }

    public String getGrpcResponseType() {
        return grpcResponseType;
    }

    public void setGrpcResponseType(String grpcResponseType) {
        this.grpcResponseType = grpcResponseType;
    }

    public String getGrpcStreaming() {
        return grpcStreaming;
    }

    public void setGrpcStreaming(String grpcStreaming) {
        this.grpcStreaming = grpcStreaming;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String signature() {
        return httpMethod + " " + path;
    }

    public boolean isGrpc() {
        return "RPC".equalsIgnoreCase(httpMethod);
    }
}
