package it.fn.redfish.catalog.service;

import it.fn.redfish.catalog.support.I18n;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;

import it.fn.redfish.catalog.domain.ApiEndpoint;
import it.fn.redfish.catalog.domain.ApiModel;
import it.fn.redfish.catalog.domain.SpecFormat;
import it.fn.redfish.catalog.domain.SpecVersion;
import it.fn.redfish.catalog.repo.ApiEndpointRepository;
import it.fn.redfish.catalog.repo.ApiModelRepository;
import it.fn.redfish.catalog.repo.SpecVersionRepository;
import it.fn.redfish.catalog.spec.EndpointView;
import it.fn.redfish.catalog.spec.EndpointViewFactory;
import it.fn.redfish.catalog.spec.SchemaRenderer;
import it.fn.redfish.catalog.support.NotFoundException;

@Service
public class SpecVersionService {

    private final SpecVersionRepository specVersions;
    private final ApiEndpointRepository endpoints;
    private final ApiModelRepository models;
    private final EndpointViewFactory viewFactory;
    private final SchemaRenderer renderer;
    private final CurlBuilder curlBuilder;

    public SpecVersionService(SpecVersionRepository specVersions, ApiEndpointRepository endpoints,
                              ApiModelRepository models, EndpointViewFactory viewFactory, SchemaRenderer renderer,
                              CurlBuilder curlBuilder) {
        this.specVersions = specVersions;
        this.endpoints = endpoints;
        this.models = models;
        this.viewFactory = viewFactory;
        this.renderer = renderer;
        this.curlBuilder = curlBuilder;
    }

    @Transactional(readOnly = true)
    public List<SpecVersion> versionsOf(Long serviceId) {
        return specVersions.findByServiceIdOrderByRevisionDesc(serviceId);
    }

    @Transactional(readOnly = true)
    public Optional<SpecVersion> currentVersion(Long serviceId) {
        return specVersions.findByServiceIdAndCurrentTrue(serviceId);
    }

    @Transactional(readOnly = true)
    public SpecVersion version(Long serviceId, Long versionId) {
        return specVersions.findById(versionId)
                .filter(v -> v.getService().getId().equals(serviceId))
                .orElseThrow(() -> NotFoundException.of(I18n.text("message.revision"), versionId));
    }

    @Transactional(readOnly = true)
    public Optional<SpecVersion> resolveVersion(Long serviceId, Long versionId) {
        return versionId == null ? currentVersion(serviceId) : Optional.of(version(serviceId, versionId));
    }

    @Transactional(readOnly = true)
    public List<ApiEndpoint> endpointsOf(Long specVersionId) {
        return endpoints.findBySpecVersionIdOrderBySortOrderAsc(specVersionId);
    }

    @Transactional(readOnly = true)
    public List<ApiModel> modelsOf(Long specVersionId) {
        return models.findBySpecVersionIdOrderBySortOrderAscNameAsc(specVersionId);
    }

    @Transactional(readOnly = true)
    public ApiEndpoint endpoint(Long endpointId) {
        return endpoints.findById(endpointId).orElseThrow(() -> NotFoundException.of("Endpoint", endpointId));
    }

    @Transactional(readOnly = true)
    public ApiModel model(Long modelId) {
        return models.findByIdWithRelations(modelId)
                .orElseThrow(() -> NotFoundException.of(I18n.text("message.model"), modelId));
    }

    @Transactional(readOnly = true)
    public EndpointView buildView(ApiEndpoint endpoint, String baseUrl) {
        List<ApiModel> versionModels = modelsOf(endpoint.getSpecVersion().getId());
        Map<String, JsonNode> index = viewFactory.modelsIndex(versionModels);
        JsonNode securitySchemes = securitySchemesOf(endpoint.getSpecVersion());
        EndpointView view = viewFactory.build(endpoint, index, securitySchemes);
        view.setCurl(curlBuilder.fromEndpoint(view, baseUrl));
        return view;
    }

    @Transactional(readOnly = true)
    public JsonNode securitySchemesOf(SpecVersion version) {
        if (version.getSpecFormat() == SpecFormat.PROTO) {
            return null;
        }
        JsonNode root = renderer.read(version.getRawContent());
        if (root == null) {
            return null;
        }
        JsonNode components = root.get("components");
        if (components != null && components.get("securitySchemes") != null) {
            return components.get("securitySchemes");
        }

        return root.get("securityDefinitions");
    }

    @Transactional(readOnly = true)
    public String suggestedBaseUrl(List<it.fn.redfish.catalog.domain.ServiceEnvironment> environments) {
        return environments.stream()
                .filter(e -> e.getBaseUrl() != null && !e.getBaseUrl().isBlank())
                .map(it.fn.redfish.catalog.domain.ServiceEnvironment::getBaseUrl)
                .findFirst()
                .orElse("");
    }

    public SchemaRenderer renderer() {
        return renderer;
    }
}
