package it.fn.redfish.catalog.spec;

import it.fn.redfish.catalog.support.I18n;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import it.fn.redfish.catalog.domain.ModelKind;
import it.fn.redfish.catalog.domain.SpecFormat;
import it.fn.redfish.catalog.support.BusinessException;
import it.fn.redfish.catalog.support.Text;

@Component
public class OpenApiSpecParser {

    private static final Logger log = LoggerFactory.getLogger(OpenApiSpecParser.class);
    private static final int MAX_WARNINGS = 25;

    public ParsedSpec parse(String content, SpecFormat declaredFormat) {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setResolveFully(false);
        options.setResolveRequestBody(true);
        options.setResolveResponses(true);

        SwaggerParseResult result;
        try {
            result = new OpenAPIParser().readContents(content, null, options);
        } catch (RuntimeException e) {
            log.debug(I18n.text("message.openapi.parsing.failed"), e);
            throw new BusinessException(I18n.text("message.cannot.parse.specification") + e.getMessage(), e);
        }

        OpenAPI api = result == null ? null : result.getOpenAPI();
        if (api == null) {
            String detail = result == null || result.getMessages() == null || result.getMessages().isEmpty()
                    ? I18n.text("message.invalid.content")
                    : String.join("; ", result.getMessages().subList(0, Math.min(5, result.getMessages().size())));
            throw new BusinessException(I18n.text("message.invalid.openapi.specification") + detail);
        }

        ParsedSpec parsed = new ParsedSpec(declaredFormat);
        if (result.getMessages() != null) {
            result.getMessages().stream().filter(m -> m != null && !m.isBlank()).limit(MAX_WARNINGS)
                    .forEach(parsed.getWarnings()::add);
        }

        if (api.getInfo() != null) {
            parsed.setTitle(api.getInfo().getTitle());
            parsed.setVersion(api.getInfo().getVersion());
            parsed.setDescription(api.getInfo().getDescription());
        }
        if (api.getServers() != null && !api.getServers().isEmpty()) {
            parsed.setServersJson(Json.pretty(api.getServers()));
        }
        if (api.getComponents() != null && api.getComponents().getSecuritySchemes() != null
                && !api.getComponents().getSecuritySchemes().isEmpty()) {
            parsed.setSecuritySchemesJson(Json.pretty(api.getComponents().getSecuritySchemes()));
        }

        extractModels(api, parsed);
        extractEndpoints(api, parsed);

        if (parsed.getEndpoints().isEmpty()) {
            parsed.getWarnings().add(I18n.text("message.the.specification.contains.no.operations.empty.paths"));
        }
        return parsed;
    }

    private void extractModels(OpenAPI api, ParsedSpec parsed) {
        if (api.getComponents() == null || api.getComponents().getSchemas() == null) {
            return;
        }
        Map<String, Schema> schemas = api.getComponents().getSchemas();
        schemas.forEach((name, schema) -> parsed.getModels().add(new ParsedSpec.ParsedModel(
                name,
                isEnum(schema) ? ModelKind.ENUM : ModelKind.SCHEMA,
                schema.getDescription(),
                Json.pretty(schema))));
    }

    private boolean isEnum(Schema<?> schema) {
        return schema.getEnum() != null && !schema.getEnum().isEmpty();
    }

    private void extractEndpoints(OpenAPI api, ParsedSpec parsed) {
        if (api.getPaths() == null) {
            return;
        }
        api.getPaths().forEach((path, item) -> {
            for (Map.Entry<PathItem.HttpMethod, Operation> entry : safeOperations(item).entrySet()) {
                parsed.getEndpoints().add(toEndpoint(api, path, item, entry.getKey(), entry.getValue()));
            }
        });
    }

    private Map<PathItem.HttpMethod, Operation> safeOperations(PathItem item) {
        Map<PathItem.HttpMethod, Operation> operations = item.readOperationsMap();
        return operations == null ? Map.of() : operations;
    }

    private ParsedEndpoint toEndpoint(OpenAPI api, String path, PathItem item,
                                      PathItem.HttpMethod method, Operation operation) {
        ParsedEndpoint endpoint = new ParsedEndpoint();
        endpoint.setHttpMethod(method.name());
        endpoint.setPath(path);
        endpoint.setOperationId(operation.getOperationId());
        endpoint.setSummary(firstNonBlank(operation.getSummary(), item.getSummary()));
        endpoint.setDescription(firstNonBlank(operation.getDescription(), item.getDescription()));
        endpoint.setDeprecated(Boolean.TRUE.equals(operation.getDeprecated()));
        if (operation.getTags() != null && !operation.getTags().isEmpty()) {
            endpoint.setTags(String.join(", ", operation.getTags()));
        }

        List<io.swagger.v3.oas.models.parameters.Parameter> parameters = new ArrayList<>();
        if (item.getParameters() != null) {
            parameters.addAll(item.getParameters());
        }
        if (operation.getParameters() != null) {
            parameters.addAll(operation.getParameters());
        }
        if (!parameters.isEmpty()) {
            endpoint.setParametersJson(Json.pretty(parameters));
        }

        if (operation.getRequestBody() != null) {
            endpoint.setRequestBodyJson(Json.pretty(operation.getRequestBody()));
            endpoint.setConsumes(contentTypes(operation.getRequestBody().getContent()));
        }
        if (operation.getResponses() != null && !operation.getResponses().isEmpty()) {
            endpoint.setResponsesJson(Json.pretty(operation.getResponses()));
            Set<String> produced = new LinkedHashSet<>();
            operation.getResponses().values().forEach(r -> {
                String types = contentTypes(r.getContent());
                if (types != null) {
                    produced.addAll(List.of(types.split(", ")));
                }
            });
            if (!produced.isEmpty()) {
                endpoint.setProduces(Text.truncate(String.join(", ", produced), 500));
            }
        }

        Object security = operation.getSecurity() != null ? operation.getSecurity() : api.getSecurity();
        if (security != null) {
            endpoint.setSecurityJson(Json.pretty(security));
        }
        if (operation.getServers() != null && !operation.getServers().isEmpty()) {
            endpoint.setServersJson(Json.pretty(operation.getServers()));
        } else if (item.getServers() != null && !item.getServers().isEmpty()) {
            endpoint.setServersJson(Json.pretty(item.getServers()));
        }
        return endpoint;
    }

    private String contentTypes(io.swagger.v3.oas.models.media.Content content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        return Text.truncate(content.keySet().stream().collect(Collectors.joining(", ")), 500);
    }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b == null || b.isBlank() ? null : b;
    }

}
