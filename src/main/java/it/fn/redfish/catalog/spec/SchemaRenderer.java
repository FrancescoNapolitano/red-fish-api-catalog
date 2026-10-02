package it.fn.redfish.catalog.spec;

import it.fn.redfish.catalog.support.I18n;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Component
public class SchemaRenderer {

    private static final int MAX_DEPTH = 6;
    private static final Set<String> CONSTRAINT_KEYS = Set.of(
            "minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum", "minLength", "maxLength",
            "pattern", "minItems", "maxItems", "uniqueItems", "multipleOf", "additionalPropertiesType");

    private final ObjectMapper mapper = new ObjectMapper();

    public ObjectMapper mapper() {
        return mapper;
    }

    public JsonNode read(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    public String prettyPrint(String json) {
        JsonNode node = read(json);
        if (node == null) {
            return json;
        }
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception e) {
            return json;
        }
    }

    public SchemaView toView(JsonNode schema, Map<String, JsonNode> models) {
        SchemaView root = new SchemaView();
        if (schema == null) {
            root.setType("—");
            return root;
        }
        populate(root, schema, models, new ArrayDeque<>(), 0);
        return root;
    }

    public SchemaView toView(String schemaJson, Map<String, JsonNode> models) {
        return toView(read(schemaJson), models);
    }

    private void populate(SchemaView view, JsonNode schema, Map<String, JsonNode> models,
                          Deque<String> refStack, int depth) {
        JsonNode resolved = schema;

        String ref = textOf(schema, "$ref");
        if (ref != null) {
            String name = refName(ref);
            view.setRefName(name);
            if (refStack.contains(name) || depth >= MAX_DEPTH) {
                view.setTruncated(true);
                view.setType("object");
                return;
            }
            JsonNode target = models.get(name);
            if (target == null) {
                view.setType("object");
                view.setTruncated(true);
                return;
            }
            refStack.push(name);
            populate(view, merge(target, schema), models, refStack, depth + 1);
            refStack.pop();
            view.setRefName(name);
            return;
        }

        for (String composite : List.of("allOf", "oneOf", "anyOf")) {
            JsonNode node = resolved.get(composite);
            if (node != null && node.isArray() && !node.isEmpty()) {
                handleComposite(view, composite, (ArrayNode) node, resolved, models, refStack, depth);
                return;
            }
        }

        view.setType(textOf(resolved, "type") != null ? textOf(resolved, "type") : inferType(resolved));
        view.setFormat(textOf(resolved, "format"));
        if (view.getDescription() == null) {
            view.setDescription(textOf(resolved, "description"));
        }
        view.setDeprecated(boolOf(resolved, "deprecated"));
        view.setNullable(boolOf(resolved, "nullable"));
        view.setReadOnly(boolOf(resolved, "readOnly"));
        view.setWriteOnly(boolOf(resolved, "writeOnly"));
        view.setProtoType(textOf(resolved, "protoType"));
        if (resolved.hasNonNull("fieldNumber")) {
            view.setFieldNumber(resolved.get("fieldNumber").asInt());
        }
        view.setOneofName(textOf(resolved, "oneof"));
        view.setDefaultValue(valueAsText(resolved.get("default")));
        view.setExample(valueAsText(resolved.get("example")));
        view.setConstraints(constraintsOf(resolved));

        JsonNode enumNode = resolved.get("enum");
        if (enumNode != null && enumNode.isArray()) {
            List<String> values = new ArrayList<>();
            enumNode.forEach(v -> values.add(valueAsText(v)));
            view.setEnumValues(values);
        }

        if ("array".equals(view.getType())) {
            JsonNode items = resolved.get("items");
            if (items != null) {
                SchemaView itemView = new SchemaView();
                itemView.setName("[elemento]");
                populate(itemView, items, models, refStack, depth + 1);
                if (itemView.getRefName() != null) {
                    view.setRefName(itemView.getRefName());
                }
                if (!itemView.isLeaf()) {
                    view.getChildren().addAll(itemView.getChildren());
                } else if (itemView.getRefName() == null) {
                    view.setFormat(itemView.getTypeLabel());
                }
            }
            return;
        }

        JsonNode properties = resolved.get("properties");
        if (properties != null && properties.isObject()) {
            Set<String> requiredNames = requiredNames(resolved);
            Iterator<Map.Entry<String, JsonNode>> it = properties.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> entry = it.next();
                SchemaView child = new SchemaView();
                child.setName(entry.getKey());
                child.setRequired(requiredNames.contains(entry.getKey()));
                populate(child, entry.getValue(), models, refStack, depth + 1);
                view.getChildren().add(child);
            }
        }
    }

    private void handleComposite(SchemaView view, String kind, ArrayNode parts, JsonNode owner,
                                 Map<String, JsonNode> models, Deque<String> refStack, int depth) {
        view.setType("object");
        view.setDescription(firstNonBlank(view.getDescription(), textOf(owner, "description")));
        if ("allOf".equals(kind)) {
            for (JsonNode part : parts) {
                SchemaView partial = new SchemaView();
                populate(partial, part, models, refStack, depth + 1);
                view.getChildren().addAll(partial.getChildren());
                if (partial.getRefName() != null && view.getRefName() == null) {
                    view.setRefName(partial.getRefName());
                }
            }
            return;
        }

        int index = 1;
        for (JsonNode part : parts) {
            SchemaView alternative = new SchemaView();
            alternative.setName(("oneOf".equals(kind) ? I18n.text("message.option") : I18n.text("message.variant")) + index++);
            populate(alternative, part, models, refStack, depth + 1);
            view.getChildren().add(alternative);
        }
    }

    private JsonNode merge(JsonNode target, JsonNode local) {
        if (!(target instanceof ObjectNode targetObject)) {
            return target;
        }
        ObjectNode copy = targetObject.deepCopy();
        local.fields().forEachRemaining(entry -> {
            if (!"$ref".equals(entry.getKey())) {
                copy.set(entry.getKey(), entry.getValue());
            }
        });
        return copy;
    }

    private Set<String> requiredNames(JsonNode schema) {
        JsonNode required = schema.get("required");
        if (required == null || !required.isArray()) {
            return Set.of();
        }
        Set<String> names = new java.util.LinkedHashSet<>();
        required.forEach(n -> names.add(n.asText()));
        return names;
    }

    private String inferType(JsonNode schema) {
        if (schema.has("properties")) {
            return "object";
        }
        if (schema.has("items")) {
            return "array";
        }
        if (schema.has("enum")) {
            return "string";
        }
        return "object";
    }

    private String constraintsOf(JsonNode schema) {
        List<String> parts = new ArrayList<>();
        for (String key : CONSTRAINT_KEYS) {
            JsonNode value = schema.get(key);
            if (value != null && !value.isNull()) {
                parts.add(key + ": " + valueAsText(value));
            }
        }
        return parts.isEmpty() ? null : String.join(" · ", parts);
    }

    public static String refName(String ref) {
        if (ref == null) {
            return null;
        }
        int slash = ref.lastIndexOf('/');
        return slash < 0 ? ref : ref.substring(slash + 1);
    }

    public String sampleJson(JsonNode schema, Map<String, JsonNode> models) {
        if (schema == null) {
            return null;
        }
        try {
            JsonNode sample = sample(schema, models, new ArrayDeque<>(), 0);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(sample);
        } catch (Exception e) {
            return null;
        }
    }

    private JsonNode sample(JsonNode schema, Map<String, JsonNode> models, Deque<String> refStack, int depth) {
        if (schema == null || depth > MAX_DEPTH) {
            return mapper.nullNode();
        }
        JsonNode example = schema.get("example");
        if (example != null && !example.isNull()) {
            return example;
        }
        String ref = textOf(schema, "$ref");
        if (ref != null) {
            String name = refName(ref);
            if (refStack.contains(name)) {
                return mapper.nullNode();
            }
            JsonNode target = models.get(name);
            if (target == null) {
                return mapper.getNodeFactory().textNode(name);
            }
            refStack.push(name);
            JsonNode result = sample(target, models, refStack, depth + 1);
            refStack.pop();
            return result;
        }
        JsonNode allOf = schema.get("allOf");
        if (allOf != null && allOf.isArray()) {
            ObjectNode merged = mapper.createObjectNode();
            for (JsonNode part : allOf) {
                JsonNode partial = sample(part, models, refStack, depth + 1);
                if (partial.isObject()) {
                    partial.fields().forEachRemaining(e -> merged.set(e.getKey(), e.getValue()));
                }
            }
            return merged;
        }
        for (String kind : List.of("oneOf", "anyOf")) {
            JsonNode node = schema.get(kind);
            if (node != null && node.isArray() && !node.isEmpty()) {
                return sample(node.get(0), models, refStack, depth + 1);
            }
        }

        JsonNode enumNode = schema.get("enum");
        if (enumNode != null && enumNode.isArray() && !enumNode.isEmpty()) {
            return enumNode.get(0);
        }

        String type = textOf(schema, "type");
        if (type == null) {
            type = inferType(schema);
        }
        String format = textOf(schema, "format");

        return switch (type) {
            case "array" -> {
                ArrayNode array = mapper.createArrayNode();
                array.add(sample(schema.get("items"), models, refStack, depth + 1));
                yield array;
            }
            case "object" -> {
                ObjectNode object = mapper.createObjectNode();
                JsonNode properties = schema.get("properties");
                if (properties != null && properties.isObject()) {
                    properties.fields().forEachRemaining(entry ->
                            object.set(entry.getKey(), sample(entry.getValue(), models, refStack, depth + 1)));
                }
                yield object;
            }
            case "integer" -> mapper.getNodeFactory().numberNode("int64".equals(format) ? 1234567890L : 1);
            case "number" -> mapper.getNodeFactory().numberNode(1.5);
            case "boolean" -> mapper.getNodeFactory().booleanNode(true);
            default -> mapper.getNodeFactory().textNode(sampleString(format, textOf(schema, "protoType")));
        };
    }

    private String sampleString(String format, String protoType) {
        if (format == null) {
            return "bytes".equals(protoType) ? "base64==" : "string";
        }
        return switch (format) {
            case "date" -> "2026-01-31";
            case "date-time" -> "2026-01-31T10:15:30Z";
            case "uuid" -> "3fa85f64-5717-4562-b3fc-2c963f66afa6";
            case "email" -> "nome.cognome@example.com";
            case "uri", "url" -> "https://example.com/risorsa";
            case "byte" -> "base64==";
            case "password" -> "********";
            case "int64" -> "1234567890";
            case "iban" -> "IT60X0542811101000000123456";
            default -> "string";
        };
    }

    public Map<String, JsonNode> modelsIndex(List<it.fn.redfish.catalog.domain.ApiModel> models) {
        Map<String, JsonNode> index = new LinkedHashMap<>();
        for (var model : models) {
            JsonNode node = read(model.getSchemaJson());
            if (node != null) {
                index.put(model.getName(), node);

                int dot = model.getName().lastIndexOf('.');
                if (dot > 0) {
                    index.putIfAbsent(model.getName().substring(dot + 1), node);
                }
            }
        }
        return index;
    }

    private static String textOf(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static boolean boolOf(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.asBoolean(false);
    }

    private static String valueAsText(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.isTextual() ? node.asText() : node.toString();
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }
}
