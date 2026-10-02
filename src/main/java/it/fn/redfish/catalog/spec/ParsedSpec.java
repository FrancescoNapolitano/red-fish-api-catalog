package it.fn.redfish.catalog.spec;

import java.util.ArrayList;
import java.util.List;

import it.fn.redfish.catalog.domain.ModelKind;
import it.fn.redfish.catalog.domain.SpecFormat;

public class ParsedSpec {

    public record ParsedModel(String name, ModelKind kind, String description, String schemaJson) {
    }

    private final SpecFormat format;
    private String title;
    private String version;
    private String description;
    private String serversJson;
    private String securitySchemesJson;
    private final List<ParsedEndpoint> endpoints = new ArrayList<>();
    private final List<ParsedModel> models = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    public ParsedSpec(SpecFormat format) {
        this.format = format;
    }

    public SpecFormat getFormat() {
        return format;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getServersJson() {
        return serversJson;
    }

    public void setServersJson(String serversJson) {
        this.serversJson = serversJson;
    }

    public String getSecuritySchemesJson() {
        return securitySchemesJson;
    }

    public void setSecuritySchemesJson(String securitySchemesJson) {
        this.securitySchemesJson = securitySchemesJson;
    }

    public List<ParsedEndpoint> getEndpoints() {
        return endpoints;
    }

    public List<ParsedModel> getModels() {
        return models;
    }

    public List<String> getWarnings() {
        return warnings;
    }
}
