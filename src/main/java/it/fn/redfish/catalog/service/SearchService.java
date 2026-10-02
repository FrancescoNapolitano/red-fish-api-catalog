package it.fn.redfish.catalog.service;

import it.fn.redfish.catalog.domain.ServiceType;
import it.fn.redfish.catalog.domain.ServiceStatus;
import org.springframework.data.domain.PageRequest;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.fn.redfish.catalog.domain.ApiEndpoint;
import it.fn.redfish.catalog.domain.ApiGroup;
import it.fn.redfish.catalog.domain.ApiModel;
import it.fn.redfish.catalog.domain.ApiService;
import it.fn.redfish.catalog.domain.Tag;
import it.fn.redfish.catalog.repo.ApiEndpointRepository;
import it.fn.redfish.catalog.repo.ApiGroupRepository;
import it.fn.redfish.catalog.repo.ApiModelRepository;
import it.fn.redfish.catalog.repo.ApiServiceRepository;
import it.fn.redfish.catalog.repo.TagRepository;

@Service
public class SearchService {

    private static final int PAGE_SIZE = 20;

    public record SearchResults(String query, List<ApiGroup> groups, List<ApiService> services,
                                List<ApiEndpoint> endpoints, List<ApiModel> models, List<Tag> tags, int page, boolean hasNext) {

        public int total() {
            return groups.size() + services.size() + endpoints.size() + models.size() + tags.size();
        }

        public boolean isEmpty() {
            return total() == 0;
        }
    }

    private final ApiGroupRepository groups;
    private final ApiServiceRepository services;
    private final ApiEndpointRepository endpoints;
    private final ApiModelRepository models;
    private final TagRepository tags;

    public SearchService(ApiGroupRepository groups, ApiServiceRepository services, ApiEndpointRepository endpoints,
                         ApiModelRepository models, TagRepository tags) {
        this.groups = groups;
        this.services = services;
        this.endpoints = endpoints;
        this.models = models;
        this.tags = tags;
    }

    @Transactional(readOnly = true)
    public SearchResults search(String rawQuery, String groupPath,
                                ServiceType type,
                                ServiceStatus status, int page) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        int safePage = Math.max(0, Math.min(page, 10000));
        if (query.length() < 2) {
            return new SearchResults(query, List.of(), List.of(), List.of(), List.of(), List.of(), safePage, false);
        }
        var pageable = PageRequest.of(safePage, PAGE_SIZE);
        var g = groups.search(query, groupPath, type, status, pageable);
        var s = services.search(query, groupPath, type, status, pageable);
        var e = endpoints.search(query, groupPath, type, status, pageable);
        var m = models.search(query, groupPath, type, status, pageable);
        var t = tags.search(query, groupPath, type, status, pageable);
        return new SearchResults(query, g.getContent(), s.getContent(), e.getContent(), m.getContent(), t.getContent(),
                safePage, g.hasNext() || s.hasNext() || e.hasNext() || m.hasNext() || t.hasNext());
    }
}
