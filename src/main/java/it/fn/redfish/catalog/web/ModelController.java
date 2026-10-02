package it.fn.redfish.catalog.web;

import it.fn.redfish.catalog.support.I18n;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.fasterxml.jackson.databind.JsonNode;

import it.fn.redfish.catalog.domain.ApiEndpoint;
import it.fn.redfish.catalog.domain.ApiModel;
import it.fn.redfish.catalog.domain.ApiService;
import it.fn.redfish.catalog.domain.Perm;
import it.fn.redfish.catalog.domain.SpecVersion;
import it.fn.redfish.catalog.security.PermissionEvaluator;
import it.fn.redfish.catalog.service.ServiceCatalogService;
import it.fn.redfish.catalog.service.SpecVersionService;
import it.fn.redfish.catalog.spec.ModelReferences;
import it.fn.redfish.catalog.spec.SchemaRenderer;
import it.fn.redfish.catalog.support.BusinessException;

@Controller
@RequestMapping("/services/{serviceId}/models")
public class ModelController {

    private final ServiceCatalogService catalog;
    private final SpecVersionService specVersions;
    private final ServiceBreadcrumbs breadcrumbs;
    private final PermissionEvaluator perm;

    public ModelController(ServiceCatalogService catalog, SpecVersionService specVersions,
                           ServiceBreadcrumbs breadcrumbs, PermissionEvaluator perm) {
        this.catalog = catalog;
        this.specVersions = specVersions;
        this.breadcrumbs = breadcrumbs;
        this.perm = perm;
    }

    @GetMapping
    public String list(@PathVariable Long serviceId,
                       @RequestParam(required = false) Long versionId,
                       @RequestParam(required = false) Long endpointId,
                       Model model) {
        ApiService service = catalog.get(serviceId);
        perm.requireOnService(Perm.CATALOG_VIEW, service);

        Optional<SpecVersion> version = specVersions.resolveVersion(serviceId, versionId);
        List<ApiModel> all = version.map(v -> specVersions.modelsOf(v.getId())).orElseGet(List::of);
        ApiEndpoint endpoint = endpointId == null ? null : endpointOf(serviceId, endpointId);

        model.addAttribute("service", service);
        model.addAttribute("selectedVersion", version.orElse(null));
        model.addAttribute("endpoint", endpoint);
        model.addAttribute("models", endpoint == null ? all : usedBy(endpoint, all));
        model.addAttribute("totalCount", all.size());
        model.addAttribute("activeGroupPath", service.getGroup().getPath());
        model.addAttribute("breadcrumb", breadcrumbs.of(service,
                endpoint == null ? I18n.text("ui.models.and.schemas")
                        : I18n.text("message.models.for") + endpoint.getHttpMethod() + " " + endpoint.getPath()));
        return "services/models";
    }

    @GetMapping("/{modelId}")
    public String detail(@PathVariable Long serviceId, @PathVariable Long modelId, Model model) {
        ApiService service = catalog.get(serviceId);
        perm.requireOnService(Perm.CATALOG_VIEW, service);

        ApiModel apiModel = specVersions.model(modelId);
        if (apiModel.getService() == null || !apiModel.getService().getId().equals(serviceId)) {
            throw new BusinessException(I18n.text("message.the.model.does.not.belong.to.the.selected.service"));
        }

        SchemaRenderer renderer = specVersions.renderer();
        List<ApiModel> siblings = specVersions.modelsOf(apiModel.getSpecVersion().getId());
        Map<String, JsonNode> index = renderer.modelsIndex(siblings);
        JsonNode schema = renderer.read(apiModel.getSchemaJson());

        model.addAttribute("service", service);
        model.addAttribute("selectedVersion", apiModel.getSpecVersion());
        model.addAttribute("apiModel", apiModel);
        model.addAttribute("schema", renderer.toView(apiModel.getSchemaJson(), index));

        model.addAttribute("sample", schema == null ? null : renderer.sampleJson(schema, index));
        model.addAttribute("rawSchema", renderer.prettyPrint(apiModel.getSchemaJson()));
        model.addAttribute("referenced", referencedModels(schema, index, siblings, apiModel));
        model.addAttribute("activeGroupPath", service.getGroup().getPath());
        model.addAttribute("breadcrumb", breadcrumbs.of(service, apiModel.getName()));
        return "services/model";
    }

    private ApiEndpoint endpointOf(Long serviceId, Long endpointId) {
        ApiEndpoint endpoint = specVersions.endpoint(endpointId);
        if (!endpoint.getService().getId().equals(serviceId)) {
            throw new BusinessException(I18n.text("message.the.endpoint.does.not.belong.to.the.selected.service"));
        }
        return endpoint;
    }

    private List<ApiModel> usedBy(ApiEndpoint endpoint, List<ApiModel> all) {
        SchemaRenderer renderer = specVersions.renderer();
        Map<String, JsonNode> index = renderer.modelsIndex(all);
        Set<String> names = ModelReferences.collect(index,
                renderer.read(endpoint.getRequestBodyJson()),
                renderer.read(endpoint.getResponsesJson()));
        return all.stream().filter(m -> names.contains(m.getName())).toList();
    }

    private List<ApiModel> referencedModels(JsonNode schema, Map<String, JsonNode> index,
                                            List<ApiModel> siblings, ApiModel current) {
        Set<String> names = ModelReferences.collect(index, schema);
        return siblings.stream()
                .filter(m -> names.contains(m.getName()))
                .filter(m -> !m.getId().equals(current.getId()))
                .toList();
    }
}
