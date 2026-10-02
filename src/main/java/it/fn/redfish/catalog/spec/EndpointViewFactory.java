package it.fn.redfish.catalog.spec;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import it.fn.redfish.catalog.domain.ApiEndpoint;
import it.fn.redfish.catalog.domain.ApiModel;

@Component
public class EndpointViewFactory {

    private final SchemaRenderer renderer;

    public EndpointViewFactory(SchemaRenderer renderer) {
        this.renderer = renderer;
    }

    public Map<String, JsonNode> modelsIndex(List<ApiModel> models) {
        return renderer.modelsIndex(models);
    }

    public EndpointView build(ApiEndpoint endpoint, Map<String, JsonNode> models, JsonNode securitySchemes) {
        EndpointView view = new EndpointView(endpoint);

        readParameters(endpoint, models, view);
        readRequestBody(endpoint, models, view);
        readResponses(endpoint, models, view);
        readSecurity(endpoint, securitySchemes, view);
        readServers(endpoint, view);

        return view;
    }

    private void readParameters(ApiEndpoint endpoint, Map<String, JsonNode> models, EndpointView view) {
        JsonNode parameters = renderer.read(endpoint.getParametersJson());
        if (parameters == null || !parameters.isArray()) {
            return;
        }
        for (JsonNode parameter : parameters) {
            EndpointView.ParamView param = toParam(parameter, models, text(parameter, "in"));
            switch (param.in() == null ? "query" : param.in()) {
                case "path" -> view.getPathParams().add(param);
                case "header" -> view.getHeaderParams().add(param);
                case "cookie" -> view.getCookieParams().add(param);
                default -> view.getQueryParams().add(param);
            }
        }
    }

    private EndpointView.ParamView toParam(JsonNode node, Map<String, JsonNode> models, String in) {
        JsonNode schemaNode = node.get("schema");
        SchemaView schema = schemaNode == null ? null : renderer.toView(schemaNode, models);
        String example = text(node, "example");
        if (example == null && schema != null) {
            example = schema.getExample();
        }
        return new EndpointView.ParamView(
                text(node, "name"),
                in,
                node.path("required").asBoolean(false),
                node.path("deprecated").asBoolean(false),
                text(node, "description"),
                schema == null ? "—" : schema.getTypeLabel(),
                schema == null ? null : schema.getConstraints(),
                example,
                schema == null ? List.of() : schema.getEnumValues(),
                schema);
    }

    private void readRequestBody(ApiEndpoint endpoint, Map<String, JsonNode> models, EndpointView view) {
        JsonNode body = renderer.read(endpoint.getRequestBodyJson());
        if (body == null) {
            return;
        }
        view.setRequestBody(new EndpointView.BodyView(
                body.path("required").asBoolean(false),
                text(body, "description"),
                readContents(body.get("content"), models)));
    }

    private List<EndpointView.ContentView> readContents(JsonNode content, Map<String, JsonNode> models) {
        List<EndpointView.ContentView> contents = new ArrayList<>();
        if (content == null || !content.isObject()) {
            return contents;
        }
        Iterator<Map.Entry<String, JsonNode>> it = content.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> entry = it.next();
            JsonNode mediaType = entry.getValue();
            JsonNode schemaNode = mediaType == null ? null : mediaType.get("schema");
            SchemaView schema = schemaNode == null ? null : renderer.toView(schemaNode, models);
            String sample = explicitExample(mediaType);
            if (sample == null) {
                sample = renderer.sampleJson(schemaNode, models);
            }
            contents.add(new EndpointView.ContentView(entry.getKey(), schema, sample));
        }
        return contents;
    }

    private String explicitExample(JsonNode mediaType) {
        if (mediaType == null) {
            return null;
        }
        JsonNode example = mediaType.get("example");
        if (example != null && !example.isNull()) {
            return renderer.prettyPrint(example.toString());
        }
        JsonNode examples = mediaType.get("examples");
        if (examples != null && examples.isObject() && examples.size() > 0) {
            JsonNode first = examples.fields().next().getValue();
            JsonNode value = first == null ? null : first.get("value");
            if (value != null && !value.isNull()) {
                return renderer.prettyPrint(value.toString());
            }
        }
        return null;
    }

    private void readResponses(ApiEndpoint endpoint, Map<String, JsonNode> models, EndpointView view) {
        JsonNode responses = renderer.read(endpoint.getResponsesJson());
        if (responses == null || !responses.isObject()) {
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> it = responses.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> entry = it.next();
            JsonNode response = entry.getValue();
            List<EndpointView.ParamView> headers = new ArrayList<>();
            JsonNode headerNode = response == null ? null : response.get("headers");
            if (headerNode != null && headerNode.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> hit = headerNode.fields();
                while (hit.hasNext()) {
                    Map.Entry<String, JsonNode> header = hit.next();
                    JsonNode withName = header.getValue().deepCopy();
                    if (withName.isObject()) {
                        ((com.fasterxml.jackson.databind.node.ObjectNode) withName).put("name", header.getKey());
                    }
                    headers.add(toParam(withName, models, "header"));
                }
            }
            view.getResponses().add(new EndpointView.ResponseView(
                    entry.getKey(),
                    response == null ? null : text(response, "description"),
                    headers,
                    readContents(response == null ? null : response.get("content"), models)));
        }
        view.getResponses().sort((a, b) -> {
            boolean aNumeric = a.code().matches("\\d+");
            boolean bNumeric = b.code().matches("\\d+");
            if (aNumeric && bNumeric) {
                return Integer.compare(Integer.parseInt(a.code()), Integer.parseInt(b.code()));
            }
            if (aNumeric != bNumeric) {
                return aNumeric ? -1 : 1;
            }
            return a.code().compareTo(b.code());
        });
    }

    private void readSecurity(ApiEndpoint endpoint, JsonNode securitySchemes, EndpointView view) {
        JsonNode security = renderer.read(endpoint.getSecurityJson());
        if (security == null || !security.isArray()) {
            return;
        }
        for (JsonNode requirement : security) {
            if (!requirement.isObject()) {
                continue;
            }
            Iterator<Map.Entry<String, JsonNode>> it = requirement.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> entry = it.next();
                String name = entry.getKey();
                JsonNode scheme = securitySchemes == null ? null : securitySchemes.get(name);
                List<String> scopes = new ArrayList<>();
                if (entry.getValue() != null && entry.getValue().isArray()) {
                    entry.getValue().forEach(s -> scopes.add(s.asText()));
                }
                view.getSecurity().add(new EndpointView.SecurityView(
                        name,
                        scheme == null ? null : text(scheme, "type"),
                        scheme == null ? null : text(scheme, "scheme"),
                        scheme == null ? null : text(scheme, "in"),
                        scopes.isEmpty() ? null : String.join(", ", scopes),
                        scheme == null ? null : text(scheme, "description")));
            }
        }
    }

    private void readServers(ApiEndpoint endpoint, EndpointView view) {
        JsonNode servers = renderer.read(endpoint.getServersJson());
        if (servers == null || !servers.isArray()) {
            return;
        }
        for (JsonNode server : servers) {
            view.getServers().add(new EndpointView.ServerView(text(server, "url"), text(server, "description")));
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.isTextual() ? value.asText() : value.toString();
    }
}
