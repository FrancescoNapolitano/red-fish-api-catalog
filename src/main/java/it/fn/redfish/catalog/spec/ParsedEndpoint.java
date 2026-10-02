package it.fn.redfish.catalog.spec;

public class ParsedEndpoint {

    private String httpMethod;
    private String path;
    private String operationId;
    private String summary;
    private String description;
    private String tags;
    private boolean deprecated;
    private String parametersJson;
    private String requestBodyJson;
    private String responsesJson;
    private String securityJson;
    private String serversJson;
    private String consumes;
    private String produces;

    private String grpcService;
    private String grpcMethod;
    private String grpcRequestType;
    private String grpcResponseType;
    private String grpcStreaming;

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
}
