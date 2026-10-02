package it.fn.redfish.catalog.spec;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

public final class ModelReferences {

    private ModelReferences() {
    }

    public static Set<String> collect(Map<String, JsonNode> models, JsonNode... roots) {
        Set<String> found = new LinkedHashSet<>();
        for (JsonNode root : roots) {
            walk(root, models == null ? Map.of() : models, found);
        }
        return found;
    }

    private static void walk(JsonNode node, Map<String, JsonNode> models, Set<String> found) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> walk(child, models, found));
            return;
        }
        if (!node.isObject()) {
            return;
        }

        JsonNode ref = node.get("$ref");
        if (ref != null && ref.isTextual()) {
            String name = SchemaRenderer.refName(ref.asText());

            if (name != null && !name.isBlank() && found.add(name)) {
                walk(models.get(name), models, found);
            }
        }
        node.properties().forEach(entry -> walk(entry.getValue(), models, found));
    }
}
