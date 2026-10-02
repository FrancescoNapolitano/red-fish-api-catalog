package it.fn.redfish.catalog.domain;

import java.time.Instant;

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
@Table(name = "service_environment")
public class ServiceEnvironment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private ApiService service;

    @Column(name = "name", length = 30, nullable = false)
    private String name;

    @Column(name = "base_url", length = 1000)
    private String baseUrl;

    @Column(name = "health_check_url", length = 1000)
    private String healthCheckUrl;

    @Column(name = "spec_url", length = 1000)
    private String specUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "health_status", length = 20, nullable = false)
    private HealthStatus healthStatus = HealthStatus.UNKNOWN;

    @Column(name = "health_checked_at")
    private Instant healthCheckedAt;

    @Column(name = "health_detail", length = 500)
    private String healthDetail;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected ServiceEnvironment() {
    }

    public ServiceEnvironment(ApiService service, String name) {
        this.service = service;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public ApiService getService() {
        return service;
    }

    public void setService(ApiService service) {
        this.service = service;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getHealthCheckUrl() {
        return healthCheckUrl;
    }

    public void setHealthCheckUrl(String healthCheckUrl) {
        this.healthCheckUrl = healthCheckUrl;
    }

    public String getSpecUrl() {
        return specUrl;
    }

    public void setSpecUrl(String specUrl) {
        this.specUrl = specUrl;
    }

    public HealthStatus getHealthStatus() {
        return healthStatus;
    }

    public void setHealthStatus(HealthStatus healthStatus) {
        this.healthStatus = healthStatus;
    }

    public Instant getHealthCheckedAt() {
        return healthCheckedAt;
    }

    public void setHealthCheckedAt(Instant healthCheckedAt) {
        this.healthCheckedAt = healthCheckedAt;
    }

    public String getHealthDetail() {
        return healthDetail;
    }

    public void setHealthDetail(String healthDetail) {
        this.healthDetail = healthDetail;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public boolean isMonitored() {
        return healthCheckUrl != null && !healthCheckUrl.isBlank();
    }
}
