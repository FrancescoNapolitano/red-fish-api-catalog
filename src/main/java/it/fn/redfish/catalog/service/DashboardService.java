package it.fn.redfish.catalog.service;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.fn.redfish.catalog.domain.ApiService;
import it.fn.redfish.catalog.domain.HealthStatus;
import it.fn.redfish.catalog.domain.ServiceEnvironment;
import it.fn.redfish.catalog.domain.ServiceStatus;
import it.fn.redfish.catalog.domain.SpecVersion;
import it.fn.redfish.catalog.repo.ApiEndpointRepository;
import it.fn.redfish.catalog.repo.ApiGroupRepository;
import it.fn.redfish.catalog.repo.ApiServiceRepository;
import it.fn.redfish.catalog.repo.AppUserRepository;
import it.fn.redfish.catalog.repo.ServiceEnvironmentRepository;
import it.fn.redfish.catalog.repo.SpecVersionRepository;

@Service
public class DashboardService {

    private final ApiServiceRepository services;
    private final ApiEndpointRepository endpoints;
    private final ApiGroupRepository groups;
    private final AppUserRepository users;
    private final SpecVersionRepository specVersions;
    private final ServiceEnvironmentRepository environments;

    public DashboardService(ApiServiceRepository services, ApiEndpointRepository endpoints, ApiGroupRepository groups,
                            AppUserRepository users, SpecVersionRepository specVersions,
                            ServiceEnvironmentRepository environments) {
        this.services = services;
        this.endpoints = endpoints;
        this.groups = groups;
        this.users = users;
        this.specVersions = specVersions;
        this.environments = environments;
    }

    public record Counters(long services, long endpoints, long groups, long users, long deprecated, long environments) {
    }

    @Transactional(readOnly = true)
    public Counters counters() {
        return new Counters(
                services.count(),
                endpoints.countCurrent(),
                groups.count(),
                users.countByEnabledTrue(),
                services.countByStatus(ServiceStatus.DEPRECATED),
                environments.count());
    }

    @Transactional(readOnly = true)
    public List<SpecVersion> recentImports(int limit) {
        return specVersions.findRecentImports(PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public List<ApiService> recentlyUpdated(int limit) {
        return services.findRecentlyUpdated(PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public List<ApiService> withoutContacts() {
        return services.findWithoutContacts(PageRequest.of(0, 8));
    }

    @Transactional(readOnly = true)
    public List<ApiService> withoutEnvironmentUrl() {
        return services.findWithoutEnvironmentUrl(PageRequest.of(0, 8));
    }

    @Transactional(readOnly = true)
    public List<ServiceEnvironment> unhealthyEnvironments() {
        return environments.findMonitoredByHealthStatus(HealthStatus.DOWN);
    }
}
