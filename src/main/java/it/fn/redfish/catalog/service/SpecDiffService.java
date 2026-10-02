package it.fn.redfish.catalog.service;

import it.fn.redfish.catalog.support.I18n;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;

import it.fn.redfish.catalog.domain.ApiEndpoint;
import it.fn.redfish.catalog.domain.ApiModel;
import it.fn.redfish.catalog.domain.SpecVersion;
import it.fn.redfish.catalog.repo.ApiEndpointRepository;
import it.fn.redfish.catalog.repo.ApiModelRepository;
import it.fn.redfish.catalog.repo.SpecVersionRepository;
import it.fn.redfish.catalog.spec.SchemaRenderer;
import it.fn.redfish.catalog.support.BusinessException;
import it.fn.redfish.catalog.support.NotFoundException;

@Service
public class SpecDiffService {

    public record EndpointChange(String method, String path, String summary, List<String> changes) {
    }

    public record ModelChange(String name, List<String> changes) {
    }

    public record CompatibilityRisk(String target, String reason) {
    }

    public record SpecDiff(SpecVersion from, SpecVersion to,
                           List<EndpointChange> endpointsAdded,
                           List<EndpointChange> endpointsRemoved,
                           List<EndpointChange> endpointsChanged,
                           List<ModelChange> modelsAdded,
                           List<ModelChange> modelsRemoved,
                           List<ModelChange> modelsChanged, List<CompatibilityRisk> compatibilityRisks) {

        public boolean isEmpty() {
            return endpointsAdded.isEmpty() && endpointsRemoved.isEmpty() && endpointsChanged.isEmpty()
                    && modelsAdded.isEmpty() && modelsRemoved.isEmpty() && modelsChanged.isEmpty();
        }

        public int totalChanges() {
            return endpointsAdded.size() + endpointsRemoved.size() + endpointsChanged.size()
                    + modelsAdded.size() + modelsRemoved.size() + modelsChanged.size();
        }
    }

    private final SpecVersionRepository specVersions;
    private final ApiEndpointRepository endpoints;
    private final ApiModelRepository models;
    private final SchemaRenderer renderer;

    public SpecDiffService(SpecVersionRepository specVersions, ApiEndpointRepository endpoints,
                           ApiModelRepository models, SchemaRenderer renderer) {
        this.specVersions = specVersions;
        this.endpoints = endpoints;
        this.models = models;
        this.renderer = renderer;
    }

    @Transactional(readOnly = true)
    public SpecDiff diff(Long serviceId, Long fromVersionId, Long toVersionId) {
        SpecVersion from = load(serviceId, fromVersionId);
        SpecVersion to = load(serviceId, toVersionId);
        if (Objects.equals(from.getId(), to.getId())) {
            throw new BusinessException(I18n.text("message.select.two.different.revisions"));
        }

        Map<String, ApiEndpoint> before = indexEndpoints(from.getId());
        Map<String, ApiEndpoint> after = indexEndpoints(to.getId());

        List<EndpointChange> added = new ArrayList<>();
        List<EndpointChange> removed = new ArrayList<>();
        List<EndpointChange> changed = new ArrayList<>();

        for (Map.Entry<String, ApiEndpoint> entry : after.entrySet()) {
            ApiEndpoint newEndpoint = entry.getValue();
            ApiEndpoint oldEndpoint = before.get(entry.getKey());
            if (oldEndpoint == null) {
                added.add(describe(newEndpoint, List.of(I18n.text("message.endpoint.added"))));
            } else {
                List<String> changes = compareEndpoints(oldEndpoint, newEndpoint);
                if (!changes.isEmpty()) {
                    changed.add(describe(newEndpoint, changes));
                }
            }
        }
        before.forEach((signature, endpoint) -> {
            if (!after.containsKey(signature)) {
                removed.add(describe(endpoint, List.of(I18n.text("message.endpoint.removed"))));
            }
        });

        Map<String, ApiModel> modelsBefore = indexModels(from.getId());
        Map<String, ApiModel> modelsAfter = indexModels(to.getId());

        List<ModelChange> modelsAdded = new ArrayList<>();
        List<ModelChange> modelsRemoved = new ArrayList<>();
        List<ModelChange> modelsChanged = new ArrayList<>();

        modelsAfter.forEach((name, model) -> {
            ApiModel previous = modelsBefore.get(name);
            if (previous == null) {
                modelsAdded.add(new ModelChange(name, List.of(I18n.text("message.model.added"))));
            } else {
                List<String> changes = compareSchemas(previous.getSchemaJson(), model.getSchemaJson());
                if (!changes.isEmpty()) {
                    modelsChanged.add(new ModelChange(name, changes));
                }
            }
        });
        modelsBefore.forEach((name, model) -> {
            if (!modelsAfter.containsKey(name)) {
                modelsRemoved.add(new ModelChange(name, List.of(I18n.text("message.model.removed"))));
            }
        });

        Comparator<EndpointChange> byPath = Comparator.comparing(EndpointChange::path)
                .thenComparing(EndpointChange::method);
        added.sort(byPath);
        removed.sort(byPath);
        changed.sort(byPath);
        Comparator<ModelChange> byName = Comparator.comparing(ModelChange::name);
        modelsAdded.sort(byName);
        modelsRemoved.sort(byName);
        modelsChanged.sort(byName);

        List<CompatibilityRisk> risks = new ArrayList<>();
        before.forEach((key, oldEndpoint) -> {
            ApiEndpoint newEndpoint = after.get(key);
            String target = oldEndpoint.getHttpMethod() + " " + oldEndpoint.getPath();
            if (newEndpoint == null) {
                risks.add(new CompatibilityRisk(target, I18n.text("message.endpoint.removed")));
            } else {
                endpointRisks(oldEndpoint, newEndpoint, target, risks);
            }
        });
        modelsBefore.forEach((name, oldModel) -> {
            ApiModel newModel = modelsAfter.get(name);
            if (newModel == null) {
                risks.add(new CompatibilityRisk(name, I18n.text("message.model.removed")));
            } else {
                schemaRisks(renderer.read(oldModel.getSchemaJson()), renderer.read(newModel.getSchemaJson()), name, risks);
            }
        });
        return new SpecDiff(from, to, added, removed, changed, modelsAdded, modelsRemoved, modelsChanged, risks);
    }

    private void endpointRisks(ApiEndpoint before, ApiEndpoint after, String target, List<CompatibilityRisk> risks) {
        Map<String, JsonNode> oldParams = parametersByKey(before.getParametersJson());
        Map<String, JsonNode> newParams = parametersByKey(after.getParametersJson());
        newParams.forEach((key, value) -> {
            JsonNode previous = oldParams.get(key);
            boolean required = value.path("required").asBoolean() || "path".equals(value.path("in").asText());
            if (required && (previous == null || !previous.path("required").asBoolean())) {
                risks.add(new CompatibilityRisk(target, I18n.text("message.required.parameter.added") + key));
            }
            if (previous != null) {
                schemaRisks(previous.path("schema"), value.path("schema"), target + " · " + key, risks);
            }
        });
        JsonNode oldBody = renderer.read(before.getRequestBodyJson());
        JsonNode newBody = renderer.read(after.getRequestBodyJson());
        if (newBody != null && newBody.path("required").asBoolean()
                && (oldBody == null || !oldBody.path("required").asBoolean())) {
            risks.add(new CompatibilityRisk(target, I18n.text("message.request.body.is.now.required")));
        }
        if (oldBody != null) {
            if (newBody == null) {
                risks.add(new CompatibilityRisk(target, I18n.text("message.request.body.removed")));
            } else {
                contentRisks(oldBody.path("content"), newBody.path("content"), target + I18n.text("message.request"), risks);
            }
        }
        JsonNode oldResponses = renderer.read(before.getResponsesJson());
        JsonNode newResponses = renderer.read(after.getResponsesJson());
        for (String code : fieldNames(oldResponses)) {
            if (newResponses == null || !newResponses.has(code)) {
                risks.add(new CompatibilityRisk(target, I18n.text("message.response.removed") + code));
            } else {
                contentRisks(oldResponses.path(code).path("content"), newResponses.path(code).path("content"),
                        target + I18n.text("message.response") + code, risks);
            }
        }
        if (!equalJson(before.getSecurityJson(), after.getSecurityJson())) {
            risks.add(new CompatibilityRisk(target, I18n.text("message.authentication.requirements.changed.review.clients")));
        }
    }

    private void contentRisks(JsonNode before, JsonNode after, String target, List<CompatibilityRisk> risks) {
        for (String media : fieldNames(before)) {
            if (!after.has(media)) {
                risks.add(new CompatibilityRisk(target, I18n.text("message.content.type.removed") + media));
            } else {
                schemaRisks(before.path(media).path("schema"), after.path(media).path("schema"), target + " · " + media, risks);
            }
        }
    }

    private void schemaRisks(JsonNode before, JsonNode after, String target, List<CompatibilityRisk> risks) {
        if (Objects.equals(before, after) || before == null) {
            return;
        }
        if (after == null || after.isMissingNode()) {
            risks.add(new CompatibilityRisk(target, I18n.text("message.schema.removed")));
            return;
        }
        for (String attribute : List.of("type", "format", "$ref", "oneOf", "anyOf", "allOf")) {
            if (!Objects.equals(before.get(attribute), after.get(attribute))) {
                risks.add(new CompatibilityRisk(target, I18n.text("message.definition.changed") + attribute));
            }
        }
        for (String field : difference(arrayValues(after.get("required")), arrayValues(before.get("required")))) {
            risks.add(new CompatibilityRisk(target, I18n.text("message.field.is.now.required") + field));
        }
        if (!after.path("enum").isMissingNode()) {
            if (!before.has("enum")) {
                risks.add(new CompatibilityRisk(target, I18n.text("message.allowed.values.restricted.to.an.enum")));
            } else {
                for (String value : difference(arrayValues(before.get("enum")), arrayValues(after.get("enum")))) {
                    risks.add(new CompatibilityRisk(target, I18n.text("message.enum.value.removed") + value));
                }
            }
        }
        for (String field : fieldNames(before.get("properties"))) {
            if (!after.path("properties").has(field)) {
                risks.add(new CompatibilityRisk(target, I18n.text("message.field.removed") + field));
            } else {
                schemaRisks(before.path("properties").get(field), after.path("properties").get(field), target + "." + field, risks);
            }
        }
        if (before.has("items")) {
            schemaRisks(before.get("items"), after.get("items"), target + "[]", risks);
        }
    }

    private List<String> compareEndpoints(ApiEndpoint before, ApiEndpoint after) {
        List<String> changes = new ArrayList<>();

        if (!Objects.equals(nullSafe(before.getSummary()), nullSafe(after.getSummary()))) {
            changes.add(I18n.text("message.summary.changed"));
        }
        if (!Objects.equals(nullSafe(before.getDescription()), nullSafe(after.getDescription()))) {
            changes.add(I18n.text("message.description.changed"));
        }
        if (before.isDeprecated() != after.isDeprecated()) {
            changes.add(after.isDeprecated() ? I18n.text("message.marked.as.deprecated") : I18n.text("message.no.longer.deprecated"));
        }
        if (!Objects.equals(nullSafe(before.getTags()), nullSafe(after.getTags()))) {
            changes.add(I18n.text("message.tags.changed") + nullSafe(before.getTags()) + " → " + nullSafe(after.getTags()));
        }
        changes.addAll(compareParameters(before.getParametersJson(), after.getParametersJson()));
        changes.addAll(compareRequestBody(before.getRequestBodyJson(), after.getRequestBodyJson()));
        changes.addAll(compareResponses(before.getResponsesJson(), after.getResponsesJson()));
        if (!equalJson(before.getSecurityJson(), after.getSecurityJson())) {
            changes.add(I18n.text("message.authentication.requirements.changed"));
        }
        return changes;
    }

    private List<String> compareParameters(String beforeJson, String afterJson) {
        Map<String, JsonNode> before = parametersByKey(beforeJson);
        Map<String, JsonNode> after = parametersByKey(afterJson);
        List<String> changes = new ArrayList<>();

        for (String key : difference(after.keySet(), before.keySet())) {
            changes.add(I18n.text("message.parameter.added") + key);
        }
        for (String key : difference(before.keySet(), after.keySet())) {
            changes.add(I18n.text("message.parameter.removed") + key);
        }
        for (String key : intersection(before.keySet(), after.keySet())) {
            JsonNode oldParam = before.get(key);
            JsonNode newParam = after.get(key);
            if (oldParam.path("required").asBoolean(false) != newParam.path("required").asBoolean(false)) {
                changes.add(I18n.text("message.parameter") + key + I18n.text("message.requirement")
                        + (newParam.path("required").asBoolean(false) ? I18n.text("message.added") : I18n.text("message.removed")));
            }
            if (!Objects.equals(oldParam.get("schema"), newParam.get("schema"))) {
                changes.add(I18n.text("message.parameter") + key + I18n.text("message.type.schema.changed"));
            }
        }
        return changes;
    }

    private List<String> compareRequestBody(String beforeJson, String afterJson) {
        boolean hadBody = beforeJson != null && !beforeJson.isBlank();
        boolean hasBody = afterJson != null && !afterJson.isBlank();
        if (!hadBody && hasBody) {
            return List.of(I18n.text("message.request.body.added"));
        }
        if (hadBody && !hasBody) {
            return List.of(I18n.text("message.request.body.removed"));
        }
        if (!hadBody) {
            return List.of();
        }
        JsonNode before = renderer.read(beforeJson);
        JsonNode after = renderer.read(afterJson);
        List<String> changes = new ArrayList<>();
        if (before != null && after != null) {
            if (before.path("required").asBoolean(false) != after.path("required").asBoolean(false)) {
                changes.add(I18n.text("message.request.body.requirement.changed"));
            }
            Set<String> beforeTypes = fieldNames(before.get("content"));
            Set<String> afterTypes = fieldNames(after.get("content"));
            for (String type : difference(afterTypes, beforeTypes)) {
                changes.add(I18n.text("message.request.body.content.type.added") + type);
            }
            for (String type : difference(beforeTypes, afterTypes)) {
                changes.add(I18n.text("message.request.body.content.type.removed") + type);
            }
            for (String type : intersection(beforeTypes, afterTypes)) {
                JsonNode oldSchema = before.get("content").get(type).get("schema");
                JsonNode newSchema = after.get("content").get(type).get("schema");
                if (!Objects.equals(oldSchema, newSchema)) {
                    changes.add(I18n.text("message.request.body.schema.changed") + type + ")");
                }
            }
        }
        return changes;
    }

    private List<String> compareResponses(String beforeJson, String afterJson) {
        JsonNode before = renderer.read(beforeJson);
        JsonNode after = renderer.read(afterJson);
        Set<String> beforeCodes = fieldNames(before);
        Set<String> afterCodes = fieldNames(after);
        List<String> changes = new ArrayList<>();

        for (String code : difference(afterCodes, beforeCodes)) {
            changes.add(I18n.text("message.response.added") + code);
        }
        for (String code : difference(beforeCodes, afterCodes)) {
            changes.add(I18n.text("message.response.removed.2") + code);
        }
        for (String code : intersection(beforeCodes, afterCodes)) {
            JsonNode oldResponse = before.get(code);
            JsonNode newResponse = after.get(code);
            if (!Objects.equals(oldResponse.get("content"), newResponse.get("content"))) {
                changes.add("Response " + code + I18n.text("message.schema.changed"));
            }
            if (!Objects.equals(oldResponse.get("headers"), newResponse.get("headers"))) {
                changes.add("Response " + code + I18n.text("message.headers.changed"));
            }
        }
        return changes;
    }

    private List<String> compareSchemas(String beforeJson, String afterJson) {
        JsonNode before = renderer.read(beforeJson);
        JsonNode after = renderer.read(afterJson);
        if (Objects.equals(before, after)) {
            return List.of();
        }
        List<String> changes = new ArrayList<>();
        Set<String> beforeProps = fieldNames(before == null ? null : before.get("properties"));
        Set<String> afterProps = fieldNames(after == null ? null : after.get("properties"));

        for (String prop : difference(afterProps, beforeProps)) {
            changes.add(I18n.text("message.field.added") + prop);
        }
        for (String prop : difference(beforeProps, afterProps)) {
            changes.add(I18n.text("message.field.removed") + prop);
        }
        for (String prop : intersection(beforeProps, afterProps)) {
            JsonNode oldProp = before.get("properties").get(prop);
            JsonNode newProp = after.get("properties").get(prop);
            if (!Objects.equals(oldProp, newProp)) {
                changes.add(I18n.text("message.field.changed") + prop);
            }
        }
        Set<String> beforeRequired = arrayValues(before == null ? null : before.get("required"));
        Set<String> afterRequired = arrayValues(after == null ? null : after.get("required"));
        for (String prop : difference(afterRequired, beforeRequired)) {
            changes.add(I18n.text("message.field.is.now.required") + prop);
        }
        for (String prop : difference(beforeRequired, afterRequired)) {
            changes.add(I18n.text("message.field.is.no.longer.required") + prop);
        }
        Set<String> beforeEnum = arrayValues(before == null ? null : before.get("enum"));
        Set<String> afterEnum = arrayValues(after == null ? null : after.get("enum"));
        for (String value : difference(afterEnum, beforeEnum)) {
            changes.add(I18n.text("message.enum.value.added") + value);
        }
        for (String value : difference(beforeEnum, afterEnum)) {
            changes.add(I18n.text("message.enum.value.removed") + value);
        }
        if (changes.isEmpty()) {
            changes.add(I18n.text("message.definition.changed.2"));
        }
        return changes;
    }

    private SpecVersion load(Long serviceId, Long versionId) {
        return specVersions.findById(versionId)
                .filter(v -> v.getService().getId().equals(serviceId))
                .orElseThrow(() -> NotFoundException.of(I18n.text("message.revision"), versionId));
    }

    private Map<String, ApiEndpoint> indexEndpoints(Long specVersionId) {
        Map<String, ApiEndpoint> index = new LinkedHashMap<>();
        endpoints.findBySpecVersionIdOrderBySortOrderAsc(specVersionId)
                .forEach(e -> index.put(e.signature(), e));
        return index;
    }

    private Map<String, ApiModel> indexModels(Long specVersionId) {
        Map<String, ApiModel> index = new LinkedHashMap<>();
        models.findBySpecVersionIdOrderBySortOrderAscNameAsc(specVersionId)
                .forEach(m -> index.put(m.getName(), m));
        return index;
    }

    private EndpointChange describe(ApiEndpoint endpoint, List<String> changes) {
        return new EndpointChange(endpoint.getHttpMethod(), endpoint.getPath(), endpoint.getSummary(), changes);
    }

    private Map<String, JsonNode> parametersByKey(String json) {
        Map<String, JsonNode> index = new LinkedHashMap<>();
        JsonNode node = renderer.read(json);
        if (node != null && node.isArray()) {
            for (JsonNode parameter : node) {
                String name = parameter.path("name").asText("?");
                String in = parameter.path("in").asText("query");
                index.put(name + " (" + in + ")", parameter);
            }
        }
        return index;
    }

    private Set<String> fieldNames(JsonNode node) {
        Set<String> names = new LinkedHashSet<>();
        if (node != null && node.isObject()) {
            Iterator<String> it = node.fieldNames();
            while (it.hasNext()) {
                names.add(it.next());
            }
        }
        return names;
    }

    private Set<String> arrayValues(JsonNode node) {
        Set<String> values = new LinkedHashSet<>();
        if (node != null && node.isArray()) {
            node.forEach(v -> values.add(v.asText()));
        }
        return values;
    }

    private List<String> difference(Set<String> a, Set<String> b) {
        return new TreeSet<>(a).stream().filter(item -> !b.contains(item)).toList();
    }

    private List<String> intersection(Set<String> a, Set<String> b) {
        return new TreeSet<>(a).stream().filter(b::contains).toList();
    }

    private boolean equalJson(String a, String b) {
        return Objects.equals(renderer.read(a), renderer.read(b));
    }

    private String nullSafe(String value) {
        return value == null ? "" : value.trim();
    }
}
