package it.fn.redfish.catalog.spec;

import it.fn.redfish.catalog.support.I18n;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import it.fn.redfish.catalog.domain.ModelKind;
import it.fn.redfish.catalog.domain.SpecFormat;
import it.fn.redfish.catalog.support.BusinessException;

@Component
public class ProtoSpecParser {

    private static final Pattern SYNTAX = Pattern.compile("syntax\\s*=\\s*[\"'](proto[23])[\"']\\s*;");
    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([A-Za-z0-9_.]+)\\s*;");
    private static final Pattern IMPORT = Pattern.compile("(?m)^\\s*import\\s+(?:public\\s+|weak\\s+)?[\"']([^\"']+)[\"']\\s*;");
    private static final Pattern BLOCK = Pattern.compile("^\\s*(service|message|enum|oneof)\\s+([A-Za-z0-9_]+)\\s*\\{?");
    private static final Pattern RPC = Pattern.compile(
            "^\\s*rpc\\s+([A-Za-z0-9_]+)\\s*\\(\\s*(stream\\s+)?([A-Za-z0-9_.]+)\\s*\\)\\s*"
                    + "returns\\s*\\(\\s*(stream\\s+)?([A-Za-z0-9_.]+)\\s*\\)");
    private static final Pattern FIELD = Pattern.compile(
            "^\\s*(?:(repeated|optional|required)\\s+)?"
                    + "(map\\s*<\\s*[A-Za-z0-9_.]+\\s*,\\s*[A-Za-z0-9_.<>\\s]+\\s*>|[A-Za-z0-9_.]+)\\s+"
                    + "([A-Za-z0-9_]+)\\s*=\\s*(\\d+)\\s*(?:\\[[^\\]]*\\])?\\s*;");
    private static final Pattern ENUM_VALUE = Pattern.compile("^\\s*([A-Za-z0-9_]+)\\s*=\\s*(-?\\d+)\\s*(?:\\[[^\\]]*\\])?\\s*;");
    private static final Pattern SERVICE_OPTION = Pattern.compile("^\\s*(option|reserved|extensions)\\b");

    private static final Map<String, String> SCALAR_TYPES = Map.ofEntries(
            Map.entry("double", "number"), Map.entry("float", "number"),
            Map.entry("int32", "integer"), Map.entry("int64", "integer"),
            Map.entry("uint32", "integer"), Map.entry("uint64", "integer"),
            Map.entry("sint32", "integer"), Map.entry("sint64", "integer"),
            Map.entry("fixed32", "integer"), Map.entry("fixed64", "integer"),
            Map.entry("sfixed32", "integer"), Map.entry("sfixed64", "integer"),
            Map.entry("bool", "boolean"), Map.entry("string", "string"),
            Map.entry("bytes", "string"));

    private final ObjectMapper mapper = new ObjectMapper();

    public ParsedSpec parse(String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(I18n.text("message.the.proto.file.is.empty"));
        }
        ParsedSpec parsed = new ParsedSpec(SpecFormat.PROTO);

        String source = stripBlockComments(content);
        Matcher syntaxMatcher = SYNTAX.matcher(source);
        String syntax = syntaxMatcher.find() ? syntaxMatcher.group(1) : "proto3";

        Matcher packageMatcher = PACKAGE.matcher(source);
        String protoPackage = packageMatcher.find() ? packageMatcher.group(1) : "";

        Matcher importMatcher = IMPORT.matcher(source);
        List<String> imports = new ArrayList<>();
        while (importMatcher.find()) {
            imports.add(importMatcher.group(1));
        }
        if (!imports.isEmpty()) {
            parsed.getWarnings().add(I18n.text("message.unresolved.imports.external.types.are.displayed.as.references")
                    + String.join(", ", imports));
        }

        Walker walker = new Walker(protoPackage, parsed);
        walker.walk(source);

        parsed.setTitle(protoPackage.isEmpty() ? I18n.text("ui.grpc.service") : protoPackage);
        parsed.setVersion(deriveVersion(protoPackage, syntax));
        parsed.setDescription(walker.fileComment);

        if (parsed.getEndpoints().isEmpty()) {
            parsed.getWarnings().add(I18n.text("message.no.service.block.with.rpc.methods.found.in.the.file"));
        }
        return parsed;
    }

    private String deriveVersion(String protoPackage, String syntax) {
        String[] segments = protoPackage.split("\\.");
        for (int i = segments.length - 1; i >= 0; i--) {
            if (segments[i].matches("v\\d+(alpha\\d*|beta\\d*)?")) {
                return segments[i];
            }
        }
        return syntax;
    }

    private final class Walker {

        private final String protoPackage;
        private final ParsedSpec parsed;
        private final Deque<Scope> stack = new ArrayDeque<>();
        private final List<String> pendingComment = new ArrayList<>();
        private String fileComment;

        Walker(String protoPackage, ParsedSpec parsed) {
            this.protoPackage = protoPackage;
            this.parsed = parsed;
        }

        void walk(String source) {
            for (String rawLine : source.split("\\R")) {
                String line = rawLine.trim();

                if (line.startsWith("//")) {
                    String comment = line.substring(2).trim();
                    if (stack.isEmpty() && fileComment == null && pendingComment.isEmpty() && !comment.isEmpty()) {

                        pendingComment.add(comment);
                    } else if (!comment.isEmpty()) {
                        pendingComment.add(comment);
                    }
                    continue;
                }
                if (line.isEmpty()) {
                    if (stack.isEmpty() && fileComment == null && !pendingComment.isEmpty()) {
                        fileComment = joinComment();
                    }
                    pendingComment.clear();
                    continue;
                }

                if (line.startsWith("}")) {
                    closeScope();
                    pendingComment.clear();
                    continue;
                }

                Matcher block = BLOCK.matcher(line);
                if (block.find()) {
                    openScope(block.group(1), block.group(2));
                    continue;
                }
                if (SERVICE_OPTION.matcher(line).find() || line.startsWith("syntax")
                        || line.startsWith("package") || line.startsWith("import")) {
                    pendingComment.clear();
                    continue;
                }

                Scope current = stack.peek();
                if (current == null) {
                    pendingComment.clear();
                    continue;
                }
                if ("service".equals(current.kind)) {
                    Matcher rpc = RPC.matcher(line);
                    if (rpc.find()) {
                        addRpc(current.name, rpc);
                    }
                    pendingComment.clear();
                    continue;
                }
                if ("enum".equals(current.kind)) {
                    Matcher value = ENUM_VALUE.matcher(line);
                    if (value.find()) {
                        current.enumValues.add(value.group(1));
                    }
                    pendingComment.clear();
                    continue;
                }

                Matcher field = FIELD.matcher(line);
                if (field.find()) {
                    addField(current, field);
                }
                pendingComment.clear();
            }

            while (!stack.isEmpty()) {
                closeScope();
            }
        }

        private void openScope(String kind, String name) {
            String comment = joinComment();
            pendingComment.clear();
            String qualified = "oneof".equals(kind) || stack.isEmpty()
                    ? name
                    : stack.peek().qualifiedName + "." + name;
            Scope scope = new Scope(kind, name, qualified, comment);

            if ("oneof".equals(kind) && stack.peek() != null) {
                scope.target = stack.peek();
            }
            stack.push(scope);
        }

        private void closeScope() {
            Scope scope = stack.poll();
            if (scope == null) {
                return;
            }
            switch (scope.kind) {
                case "message" -> parsed.getModels().add(new ParsedSpec.ParsedModel(
                        scope.qualifiedName, ModelKind.MESSAGE, scope.comment, messageJson(scope)));
                case "enum" -> parsed.getModels().add(new ParsedSpec.ParsedModel(
                        scope.qualifiedName, ModelKind.ENUM, scope.comment, enumJson(scope)));
                default -> {

                }
            }
        }

        private void addField(Scope scope, Matcher field) {
            Scope target = scope.target != null ? scope.target : scope;
            String label = field.group(1);
            String type = field.group(2).replaceAll("\\s+", "");
            String name = field.group(3);
            int number = Integer.parseInt(field.group(4));
            target.fields.add(new Field(name, type, "repeated".equals(label), "optional".equals(label),
                    "required".equals(label), number, joinComment(),
                    scope.target != null ? scope.name : null));
        }

        private void addRpc(String serviceName, Matcher rpc) {
            String method = rpc.group(1);
            boolean streamRequest = rpc.group(2) != null;
            String requestType = rpc.group(3);
            boolean streamResponse = rpc.group(4) != null;
            String responseType = rpc.group(5);

            String fullService = protoPackage.isEmpty() ? serviceName : protoPackage + "." + serviceName;
            ParsedEndpoint endpoint = new ParsedEndpoint();
            endpoint.setHttpMethod("RPC");
            endpoint.setPath("/" + fullService + "/" + method);
            endpoint.setOperationId(fullService + "." + method);
            endpoint.setSummary(joinComment());
            endpoint.setTags(serviceName);
            endpoint.setGrpcService(fullService);
            endpoint.setGrpcMethod(method);
            endpoint.setGrpcRequestType(requestType);
            endpoint.setGrpcResponseType(responseType);
            endpoint.setGrpcStreaming(streamingLabel(streamRequest, streamResponse));
            endpoint.setConsumes("application/grpc");
            endpoint.setProduces("application/grpc");
            endpoint.setRequestBodyJson(grpcBodyJson(requestType, streamRequest));
            endpoint.setResponsesJson(grpcResponsesJson(responseType, streamResponse));
            parsed.getEndpoints().add(endpoint);
        }

        private String joinComment() {
            return pendingComment.isEmpty() ? null : String.join(" ", pendingComment);
        }

        private String messageJson(Scope scope) {
            ObjectNode root = mapper.createObjectNode();
            root.put("type", "object");
            root.put("protoKind", "message");
            if (scope.comment != null) {
                root.put("description", scope.comment);
            }
            ObjectNode properties = root.putObject("properties");
            ArrayNode required = mapper.createArrayNode();
            for (Field f : scope.fields) {
                ObjectNode node = properties.putObject(f.name());
                applyType(node, f);
                node.put("fieldNumber", f.number());
                if (f.comment() != null) {
                    node.put("description", f.comment());
                }
                if (f.oneofName() != null) {
                    node.put("oneof", f.oneofName());
                }
                if (f.required()) {
                    required.add(f.name());
                }
            }
            if (!required.isEmpty()) {
                root.set("required", required);
            }
            return write(root);
        }

        private void applyType(ObjectNode node, Field field) {
            String protoType = field.type();
            node.put("protoType", protoType + (field.repeated() ? "[]" : ""));

            if (protoType.startsWith("map<")) {
                node.put("type", "object");
                node.put("additionalPropertiesType", protoType);
                return;
            }
            ObjectNode holder = node;
            if (field.repeated()) {
                node.put("type", "array");
                holder = node.putObject("items");
            }
            String jsonType = SCALAR_TYPES.get(protoType);
            if (jsonType != null) {
                holder.put("type", jsonType);
                if ("bytes".equals(protoType)) {
                    holder.put("format", "byte");
                }
                if (protoType.endsWith("64")) {
                    holder.put("format", "int64");
                }
            } else {
                holder.put("$ref", "#/components/schemas/" + qualify(protoType));
            }
        }

        private String qualify(String protoType) {
            return protoType;
        }

        private String enumJson(Scope scope) {
            ObjectNode root = mapper.createObjectNode();
            root.put("type", "string");
            root.put("protoKind", "enum");
            if (scope.comment != null) {
                root.put("description", scope.comment);
            }
            ArrayNode values = root.putArray("enum");
            scope.enumValues.forEach(values::add);
            return write(root);
        }

        private String grpcBodyJson(String type, boolean streaming) {
            ObjectNode root = mapper.createObjectNode();
            root.put("required", true);
            root.put("description", (streaming ? I18n.text("message.stream.of") : "") + type);
            ObjectNode schema = root.putObject("content").putObject("application/grpc").putObject("schema");
            if (streaming) {
                schema.put("type", "array");
                schema.putObject("items").put("$ref", "#/components/schemas/" + type);
            } else {
                schema.put("$ref", "#/components/schemas/" + type);
            }
            return write(root);
        }

        private String grpcResponsesJson(String type, boolean streaming) {
            ObjectNode root = mapper.createObjectNode();
            ObjectNode ok = root.putObject("OK");
            ok.put("description", (streaming ? I18n.text("message.stream.of") : "") + type);
            ObjectNode schema = ok.putObject("content").putObject("application/grpc").putObject("schema");
            if (streaming) {
                schema.put("type", "array");
                schema.putObject("items").put("$ref", "#/components/schemas/" + type);
            } else {
                schema.put("$ref", "#/components/schemas/" + type);
            }
            return write(root);
        }

        private String write(ObjectNode node) {
            try {
                return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
            } catch (JsonProcessingException e) {
                throw new BusinessException(I18n.text("message.error.serializing.the.proto.model"), e);
            }
        }
    }

    private String streamingLabel(boolean request, boolean response) {
        if (request && response) {
            return "BIDIRECTIONAL";
        }
        if (request) {
            return "CLIENT_STREAMING";
        }
        if (response) {
            return "SERVER_STREAMING";
        }
        return "UNARY";
    }

    private static String stripBlockComments(String content) {
        return content.replaceAll("(?s)/\\*.*?\\*/", "");
    }

    private static final class Scope {
        private final String kind;
        private final String name;
        private final String qualifiedName;
        private final String comment;
        private final List<Field> fields = new ArrayList<>();
        private final List<String> enumValues = new ArrayList<>();

        private Scope target;

        Scope(String kind, String name, String qualifiedName, String comment) {
            this.kind = kind;
            this.name = name;
            this.qualifiedName = qualifiedName;
            this.comment = comment;
        }
    }

    private record Field(String name, String type, boolean repeated, boolean optional, boolean required,
                         int number, String comment, String oneofName) {
    }

    public static Map<String, String> scalarTypes() {
        return new LinkedHashMap<>(SCALAR_TYPES);
    }
}
