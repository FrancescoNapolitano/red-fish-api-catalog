package it.fn.redfish.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "api_model")
public class ApiModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "spec_version_id", nullable = false)
    private SpecVersion specVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private ApiService service;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", length = 30, nullable = false)
    private ModelKind kind = ModelKind.SCHEMA;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "schema_json", columnDefinition = "text")
    private String schemaJson;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected ApiModel() {
    }

    public ApiModel(SpecVersion specVersion, String name, ModelKind kind) {
        this.specVersion = specVersion;
        this.service = specVersion.getService();
        this.name = name;
        this.kind = kind;
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

    public String getName() {
        return name;
    }

    public ModelKind getKind() {
        return kind;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSchemaJson() {
        return schemaJson;
    }

    public void setSchemaJson(String schemaJson) {
        this.schemaJson = schemaJson;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
