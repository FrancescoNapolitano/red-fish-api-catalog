package it.fn.redfish.catalog.service;

import it.fn.redfish.catalog.support.I18n;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import it.fn.redfish.catalog.domain.ApiEndpoint;
import it.fn.redfish.catalog.domain.ApiGroup;
import it.fn.redfish.catalog.domain.ApiModel;
import it.fn.redfish.catalog.domain.ApiService;
import it.fn.redfish.catalog.domain.Perm;
import it.fn.redfish.catalog.domain.SourceType;
import it.fn.redfish.catalog.domain.SpecFormat;
import it.fn.redfish.catalog.domain.SpecVersion;
import it.fn.redfish.catalog.repo.ApiEndpointRepository;
import it.fn.redfish.catalog.repo.ApiGroupRepository;
import it.fn.redfish.catalog.repo.ApiModelRepository;
import it.fn.redfish.catalog.repo.ApiServiceRepository;
import it.fn.redfish.catalog.repo.AppUserRepository;
import it.fn.redfish.catalog.repo.SpecVersionRepository;
import it.fn.redfish.catalog.security.PermissionEvaluator;
import it.fn.redfish.catalog.spec.OpenApiSpecParser;
import it.fn.redfish.catalog.spec.ParsedEndpoint;
import it.fn.redfish.catalog.spec.ParsedSpec;
import it.fn.redfish.catalog.spec.ProtoSpecParser;
import it.fn.redfish.catalog.spec.SpecFormatDetector;
import it.fn.redfish.catalog.support.BusinessException;
import it.fn.redfish.catalog.support.NotFoundException;
import it.fn.redfish.catalog.support.Text;
import it.fn.redfish.catalog.web.form.ImportForm;

@Service
public class ImportService {

    public record ImportOutcome(ApiService service, SpecVersion version, List<String> warnings, boolean serviceCreated) {
    }

    private final ApiGroupRepository groups;
    private final ApiServiceRepository services;
    private final SpecVersionRepository specVersions;
    private final ApiEndpointRepository endpoints;
    private final ApiModelRepository models;
    private final AppUserRepository users;
    private final SpecFormatDetector detector;
    private final OpenApiSpecParser openApiParser;
    private final ProtoSpecParser protoParser;
    private final SpecFetcher fetcher;
    private final SettingsService settings;
    private final ServiceCatalogService serviceCatalog;
    private final PermissionEvaluator perm;
    private final AuditService audit;

    public ImportService(ApiGroupRepository groups, ApiServiceRepository services, SpecVersionRepository specVersions,
                         ApiEndpointRepository endpoints, ApiModelRepository models, AppUserRepository users,
                         SpecFormatDetector detector, OpenApiSpecParser openApiParser, ProtoSpecParser protoParser,
                         SpecFetcher fetcher, SettingsService settings, ServiceCatalogService serviceCatalog,
                         PermissionEvaluator perm, AuditService audit) {
        this.groups = groups;
        this.services = services;
        this.specVersions = specVersions;
        this.endpoints = endpoints;
        this.models = models;
        this.users = users;
        this.detector = detector;
        this.openApiParser = openApiParser;
        this.protoParser = protoParser;
        this.fetcher = fetcher;
        this.settings = settings;
        this.serviceCatalog = serviceCatalog;
        this.perm = perm;
        this.audit = audit;
    }

    @Transactional
    public ImportOutcome importSpec(ImportForm form, String username) {
        SourceContent source = readSource(form);
        SpecFormat format = detector.detect(source.fileName(), source.content());

        ParsedSpec parsed = format == SpecFormat.PROTO
                ? protoParser.parse(source.content())
                : openApiParser.parse(source.content(), format);

        ApiService service;
        boolean created = false;
        if (form.getServiceId() != null) {
            service = services.findById(form.getServiceId())
                    .orElseThrow(() -> NotFoundException.of(I18n.text("ui.service"), form.getServiceId()));
            requireImportPermission(format, service.getGroup());
            if (service.getServiceType() != format.getServiceType()) {
                throw new BusinessException(I18n.text("message.the.selected.service.has.type") + service.getServiceType().getLabel()
                        + I18n.text("message.but.the.specification.has.type") + format.getLabel());
            }
        } else {
            ApiGroup group = groups.findById(requireGroupId(form))
                    .orElseThrow(() -> NotFoundException.of(I18n.text("ui.group"), form.getGroupId()));
            requireImportPermission(format, group);
            String name = firstNonBlank(form.getServiceName(), parsed.getTitle(), source.fileName());
            service = serviceCatalog.createForImport(group, name.trim(), format.getServiceType(),
                    parsed.getVersion(), parsed.getDescription());
            created = true;
        }

        int revision = specVersions.maxRevision(service.getId()) + 1;
        SpecVersion version = new SpecVersion(service, revision, format, form.getSourceType());
        version.setRawContent(source.content());
        version.setFileName(source.fileName());
        version.setContentType(source.contentType());
        version.setSizeBytes(source.size());
        version.setChecksum(sha256(source.content()));
        version.setSourceUrl(form.getSourceType() == SourceType.URL ? form.getUrl() : null);
        version.setTitle(parsed.getTitle());
        version.setDescription(parsed.getDescription());
        version.setVersionLabel(parsed.getVersion());
        version.setEndpointCount(parsed.getEndpoints().size());
        version.setModelCount(parsed.getModels().size());
        version.setNotes(Text.blankToNull(form.getNotes()));
        version.setImportedAt(Instant.now());
        version.setImportedByUsername(username);
        if (username != null) {
            users.findByUsernameIgnoreCase(username).ifPresent(version::setImportedBy);
        }
        version.setCurrent(form.isMakeCurrent());
        SpecVersion savedVersion = specVersions.save(version);

        persistEndpoints(savedVersion, parsed);
        persistModels(savedVersion, parsed);

        if (form.isMakeCurrent()) {
            specVersions.findByServiceIdOrderByRevisionDesc(service.getId()).stream()
                    .filter(v -> !v.getId().equals(savedVersion.getId()))
                    .filter(SpecVersion::isCurrent)
                    .forEach(v -> {
                        v.setCurrent(false);
                        specVersions.save(v);
                    });
            if (parsed.getVersion() != null && !parsed.getVersion().isBlank()) {
                service.setVersion(parsed.getVersion());
            }
            if (service.getDescription() == null && parsed.getDescription() != null) {
                service.setDescription(parsed.getDescription());
            }
        }
        service.setImportedAt(savedVersion.getImportedAt());
        service.setUpdatedAt(Instant.now());
        if (form.getTags() != null && !form.getTags().isBlank()) {
            service.setTags(serviceCatalog.resolveTags(form.getTags()));
        }
        services.save(service);

        audit.record("SPEC_IMPORT", "SpecVersion", savedVersion.getId(),
                "Import " + format.getLabel() + I18n.text("message.on") + service.getName() + " (r" + revision + "): "
                        + parsed.getEndpoints().size() + " endpoint, " + parsed.getModels().size() + I18n.text("message.models"));

        return new ImportOutcome(service, savedVersion, parsed.getWarnings(), created);
    }

    @Transactional
    public void deleteVersion(Long serviceId, Long versionId) {
        SpecVersion version = specVersions.findById(versionId)
                .filter(v -> v.getService().getId().equals(serviceId))
                .orElseThrow(() -> NotFoundException.of(I18n.text("message.revision"), versionId));
        boolean wasCurrent = version.isCurrent();
        specVersions.delete(version);
        specVersions.flush();
        if (wasCurrent) {
            specVersions.findByServiceIdOrderByRevisionDesc(serviceId).stream().findFirst().ifPresent(latest -> {
                latest.setCurrent(true);
                specVersions.save(latest);
            });
        }
        audit.record("SPEC_VERSION_DELETE", "SpecVersion", versionId,
                I18n.text("message.deleted.revision.r") + version.getRevision());
    }

    @Transactional
    public void makeCurrent(Long serviceId, Long versionId) {
        List<SpecVersion> all = specVersions.findByServiceIdOrderByRevisionDesc(serviceId);
        SpecVersion target = all.stream().filter(v -> v.getId().equals(versionId)).findFirst()
                .orElseThrow(() -> NotFoundException.of(I18n.text("message.revision"), versionId));
        all.forEach(v -> {
            boolean current = v.getId().equals(versionId);
            if (v.isCurrent() != current) {
                v.setCurrent(current);
                specVersions.save(v);
            }
        });
        ApiService service = target.getService();
        if (target.getVersionLabel() != null) {
            service.setVersion(target.getVersionLabel());
        }
        service.setUpdatedAt(Instant.now());
        services.save(service);
        audit.record("SPEC_VERSION_ACTIVATE", "SpecVersion", versionId,
                I18n.text("message.revision.r") + target.getRevision() + I18n.text("message.set.as.current"));
    }

    private record SourceContent(String content, String fileName, String contentType, long size) {
    }

    private SourceContent readSource(ImportForm form) {
        long maxBytes = settings.maxUploadBytes();
        if (form.getSourceType() == SourceType.URL) {
            SpecFetcher.Fetched fetched = fetcher.fetch(form.getUrl(), maxBytes);
            return new SourceContent(fetched.content(), fetched.fileName(), fetched.contentType(), fetched.size());
        }
        MultipartFile file = form.getFile();
        if (file == null || file.isEmpty()) {
            throw new BusinessException(I18n.text("message.select.a.file.to.upload"));
        }
        if (file.getSize() > maxBytes) {
            throw new BusinessException(I18n.text("message.the.file.exceeds.the.limit.of") + (maxBytes / 1024) + " KB");
        }
        try {
            String content = Text.stripBom(new String(file.getBytes(), StandardCharsets.UTF_8));
            if (content.isBlank()) {
                throw new BusinessException(I18n.text("message.the.uploaded.file.is.empty"));
            }
            return new SourceContent(content, sanitizeFileName(file.getOriginalFilename()),
                    file.getContentType(), file.getSize());
        } catch (IOException e) {
            throw new BusinessException(I18n.text("message.cannot.read.the.uploaded.file") + e.getMessage(), e);
        }
    }

    private void persistEndpoints(SpecVersion version, ParsedSpec parsed) {
        int order = 0;
        for (ParsedEndpoint parsedEndpoint : parsed.getEndpoints()) {
            ApiEndpoint endpoint = new ApiEndpoint(version, parsedEndpoint.getHttpMethod(), parsedEndpoint.getPath());
            endpoint.setOperationId(parsedEndpoint.getOperationId());
            endpoint.setSummary(Text.truncate(parsedEndpoint.getSummary(), 1000));
            endpoint.setDescription(parsedEndpoint.getDescription());
            endpoint.setTags(Text.truncate(parsedEndpoint.getTags(), 500));
            endpoint.setDeprecated(parsedEndpoint.isDeprecated());
            endpoint.setParametersJson(parsedEndpoint.getParametersJson());
            endpoint.setRequestBodyJson(parsedEndpoint.getRequestBodyJson());
            endpoint.setResponsesJson(parsedEndpoint.getResponsesJson());
            endpoint.setSecurityJson(parsedEndpoint.getSecurityJson());
            endpoint.setServersJson(parsedEndpoint.getServersJson() != null
                    ? parsedEndpoint.getServersJson() : parsed.getServersJson());
            endpoint.setConsumes(Text.truncate(parsedEndpoint.getConsumes(), 500));
            endpoint.setProduces(Text.truncate(parsedEndpoint.getProduces(), 500));
            endpoint.setGrpcService(parsedEndpoint.getGrpcService());
            endpoint.setGrpcMethod(parsedEndpoint.getGrpcMethod());
            endpoint.setGrpcRequestType(parsedEndpoint.getGrpcRequestType());
            endpoint.setGrpcResponseType(parsedEndpoint.getGrpcResponseType());
            endpoint.setGrpcStreaming(parsedEndpoint.getGrpcStreaming());
            endpoint.setSortOrder(order++);
            endpoints.save(endpoint);
        }
    }

    private void persistModels(SpecVersion version, ParsedSpec parsed) {
        int order = 0;
        for (ParsedSpec.ParsedModel parsedModel : parsed.getModels()) {
            ApiModel model = new ApiModel(version, parsedModel.name(), parsedModel.kind());
            model.setDescription(parsedModel.description());
            model.setSchemaJson(parsedModel.schemaJson());
            model.setSortOrder(order++);
            models.save(model);
        }
    }

    private void requireImportPermission(SpecFormat format, ApiGroup group) {
        String required = format == SpecFormat.PROTO ? Perm.IMPORT_GRPC : Perm.IMPORT_SWAGGER;
        perm.requireOnGroup(required, group);
    }

    private Long requireGroupId(ImportForm form) {
        if (form.getGroupId() == null) {
            throw new BusinessException(I18n.text("message.select.a.destination.group.or.an.existing.service"));
        }
        return form.getGroupId();
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "spec";
        }
        String name = fileName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "servizio";
    }

    public ParsedSpec parseOnly(String fileName, String content) {
        SpecFormat format = detector.detect(fileName, content);
        return format == SpecFormat.PROTO ? protoParser.parse(content) : openApiParser.parse(content, format);
    }
}
