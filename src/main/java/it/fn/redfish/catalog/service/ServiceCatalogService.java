package it.fn.redfish.catalog.service;

import it.fn.redfish.catalog.support.I18n;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.fn.redfish.catalog.domain.ApiEndpoint;
import it.fn.redfish.catalog.domain.ApiGroup;
import it.fn.redfish.catalog.domain.ApiService;
import it.fn.redfish.catalog.domain.AppUser;
import it.fn.redfish.catalog.domain.Contact;
import it.fn.redfish.catalog.domain.ExternalLink;
import it.fn.redfish.catalog.domain.ServiceComment;
import it.fn.redfish.catalog.domain.ServiceEnvironment;
import it.fn.redfish.catalog.domain.ServiceType;
import it.fn.redfish.catalog.domain.Tag;
import it.fn.redfish.catalog.repo.ApiEndpointRepository;
import it.fn.redfish.catalog.repo.ApiGroupRepository;
import it.fn.redfish.catalog.repo.ApiServiceRepository;
import it.fn.redfish.catalog.repo.AppUserRepository;
import it.fn.redfish.catalog.repo.ContactRepository;
import it.fn.redfish.catalog.repo.ExternalLinkRepository;
import it.fn.redfish.catalog.repo.ServiceCommentRepository;
import it.fn.redfish.catalog.repo.ServiceEnvironmentRepository;
import it.fn.redfish.catalog.repo.TagRepository;
import it.fn.redfish.catalog.support.BusinessException;
import it.fn.redfish.catalog.support.NotFoundException;
import it.fn.redfish.catalog.support.Slugs;
import it.fn.redfish.catalog.support.Text;
import it.fn.redfish.catalog.web.form.ContactForm;
import it.fn.redfish.catalog.web.form.ServiceForm;

@Service
public class ServiceCatalogService {

    public static final List<String> STANDARD_ENVIRONMENTS = List.of("DEV", "TEST", "UAT", "PRE", "PROD");

    private final ApiServiceRepository services;
    private final ApiGroupRepository groups;
    private final TagRepository tags;
    private final ContactRepository contacts;
    private final ServiceEnvironmentRepository environments;
    private final ExternalLinkRepository links;
    private final ServiceCommentRepository comments;
    private final ApiEndpointRepository endpoints;
    private final AppUserRepository users;
    private final AuditService audit;

    public ServiceCatalogService(ApiServiceRepository services, ApiGroupRepository groups, TagRepository tags,
                                 ContactRepository contacts, ServiceEnvironmentRepository environments,
                                 ExternalLinkRepository links, ServiceCommentRepository comments,
                                 ApiEndpointRepository endpoints, AppUserRepository users, AuditService audit) {
        this.services = services;
        this.groups = groups;
        this.tags = tags;
        this.contacts = contacts;
        this.environments = environments;
        this.links = links;
        this.comments = comments;
        this.endpoints = endpoints;
        this.users = users;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public ApiService get(Long id) {
        return services.findById(id).orElseThrow(() -> NotFoundException.of(I18n.text("ui.service"), id));
    }

    @Transactional(readOnly = true)
    public List<ApiService> findAll() {
        return services.findAll(org.springframework.data.domain.Sort.by("name"));
    }

    @Transactional(readOnly = true)
    public List<ApiService> findByGroup(Long groupId) {
        return services.findByGroupIdOrderByNameAsc(groupId);
    }

    @Transactional(readOnly = true)
    public List<ApiService> findInSubtree(String groupPath) {
        return services.findInSubtree(groupPath);
    }

    @Transactional(readOnly = true)
    public List<Tag> allTags() {
        return tags.findAllByOrderByNameAsc();
    }

    @Transactional
    public ApiService create(ServiceForm form) {
        ApiGroup group = requireGroup(form.getGroupId());
        String slug = resolveSlug(form.getSlug(), form.getName());
        if (services.countSlugInGroup(group.getId(), slug, null) > 0) {
            throw new BusinessException(I18n.text("message.a.service.already.exists.with.identifier") + slug + I18n.text("message.in.this.group"));
        }
        ApiService service = new ApiService(group, form.getName().trim(), slug, form.getServiceType());
        applyForm(service, form);
        ApiService saved = services.save(service);
        audit.record("SERVICE_CREATE", "ApiService", saved.getId(),
                I18n.text("message.created.service") + saved.getName() + I18n.text("message.in") + group.getPath());
        return saved;
    }

    @Transactional
    public ApiService update(Long id, ServiceForm form) {
        ApiService service = get(id);
        ApiGroup group = requireGroup(form.getGroupId());
        String slug = resolveSlug(form.getSlug(), form.getName());
        if (services.countSlugInGroup(group.getId(), slug, id) > 0) {
            throw new BusinessException(I18n.text("message.a.service.already.exists.with.identifier") + slug + I18n.text("message.in.this.group"));
        }
        service.setGroup(group);
        service.setSlug(slug);
        service.setName(form.getName().trim());
        applyForm(service, form);
        ApiService saved = services.save(service);
        audit.record("SERVICE_UPDATE", "ApiService", id, I18n.text("message.updated.service") + saved.getName());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        ApiService service = get(id);
        String name = service.getName();
        services.delete(service);
        audit.record("SERVICE_DELETE", "ApiService", id, I18n.text("message.deleted.service") + name);
    }

    private void applyForm(ApiService service, ServiceForm form) {
        service.setDescription(Text.blankToNull(form.getDescription()));
        service.setVersion(Text.blankToNull(form.getVersion()));
        service.setServiceType(form.getServiceType());
        service.setStatus(form.getStatus());
        service.setTags(resolveTags(form.getTags()));
        service.setUpdatedAt(Instant.now());
    }

    @Transactional
    public Set<Tag> resolveTags(String csv) {
        Set<Tag> resolved = new LinkedHashSet<>();
        if (csv == null || csv.isBlank()) {
            return resolved;
        }
        for (String raw : csv.split(",")) {
            String name = raw.trim();
            if (name.isEmpty()) {
                continue;
            }
            Tag tag = tags.findByNameIgnoreCase(name).orElseGet(() -> tags.save(new Tag(name, null)));
            resolved.add(tag);
        }
        return resolved;
    }

    @Transactional(readOnly = true)
    public List<Contact> contactsOfService(Long serviceId) {
        return contacts.findByServiceIdOrderByNameAsc(serviceId);
    }

    @Transactional
    public void addContact(Long serviceId, ContactForm form) {
        ApiService service = get(serviceId);
        Contact contact = Contact.forService(service);
        form.applyTo(contact);
        contacts.save(contact);
        audit.record("CONTACT_ADD", "ApiService", serviceId, I18n.text("message.contact") + contact.getName());
    }

    @Transactional
    public void deleteContact(Long serviceId, Long contactId) {
        contacts.findById(contactId)
                .filter(c -> c.getService() != null && c.getService().getId().equals(serviceId))
                .ifPresent(c -> {
                    contacts.delete(c);
                    audit.record("CONTACT_DELETE", "ApiService", serviceId, I18n.text("message.removed.contact") + c.getName());
                });
    }

    @Transactional(readOnly = true)
    public List<ServiceEnvironment> environmentsOf(Long serviceId) {
        return environments.findByServiceIdOrderBySortOrderAscNameAsc(serviceId);
    }

    @Transactional
    public void saveEnvironment(Long serviceId, String name, String baseUrl, String healthCheckUrl, String specUrl) {
        ApiService service = get(serviceId);
        String normalized = name == null ? "" : name.trim().toUpperCase();
        if (normalized.isEmpty()) {
            throw new BusinessException(I18n.text("message.the.environment.name.is.required"));
        }
        ServiceEnvironment env = environments.findByServiceIdAndNameIgnoreCase(serviceId, normalized)
                .orElseGet(() -> new ServiceEnvironment(service, normalized));
        env.setName(normalized);
        env.setBaseUrl(Text.blankToNull(baseUrl));
        env.setHealthCheckUrl(Text.blankToNull(healthCheckUrl));
        env.setSpecUrl(Text.blankToNull(specUrl));
        int index = STANDARD_ENVIRONMENTS.indexOf(normalized);
        env.setSortOrder(index < 0 ? 99 : index);
        environments.save(env);
        audit.record("ENVIRONMENT_SAVE", "ApiService", serviceId, I18n.text("message.environment") + normalized + " → " + baseUrl);
    }

    @Transactional
    public void deleteEnvironment(Long serviceId, Long environmentId) {
        environments.findById(environmentId)
                .filter(e -> e.getService().getId().equals(serviceId))
                .ifPresent(e -> {
                    environments.delete(e);
                    audit.record("ENVIRONMENT_DELETE", "ApiService", serviceId, I18n.text("message.removed.environment") + e.getName());
                });
    }

    @Transactional(readOnly = true)
    public List<ExternalLink> linksOf(Long serviceId) {
        return links.findByServiceIdOrderBySortOrderAscLabelAsc(serviceId);
    }

    @Transactional
    public void addLink(Long serviceId, String label, String url, String kind) {
        ApiService service = get(serviceId);
        if (label == null || label.isBlank() || url == null || url.isBlank()) {
            throw new BusinessException(I18n.text("message.the.link.label.and.url.are.required"));
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new BusinessException(I18n.text("message.the.link.url.must.start.with.http.or.https"));
        }
        links.save(new ExternalLink(service, label.trim(), url.trim(), Text.blankToNull(kind)));
        audit.record("LINK_ADD", "ApiService", serviceId, I18n.text("message.link") + label);
    }

    @Transactional
    public void deleteLink(Long serviceId, Long linkId) {
        links.findById(linkId)
                .filter(l -> l.getService().getId().equals(serviceId))
                .ifPresent(l -> {
                    links.delete(l);
                    audit.record("LINK_DELETE", "ApiService", serviceId, I18n.text("message.removed.link") + l.getLabel());
                });
    }

    @Transactional(readOnly = true)
    public List<ServiceComment> commentsOfService(Long serviceId) {
        return comments.findByServiceIdAndEndpointIsNullOrderByCreatedAtDesc(serviceId);
    }

    @Transactional(readOnly = true)
    public List<ServiceComment> commentsOfEndpoint(Long endpointId) {
        return comments.findByEndpointIdOrderByCreatedAtDesc(endpointId);
    }

    @Transactional
    public void addComment(Long serviceId, Long endpointId, String username, String body) {
        if (body == null || body.isBlank()) {
            throw new BusinessException(I18n.text("message.the.comment.cannot.be.empty"));
        }
        ApiService service = get(serviceId);
        ApiEndpoint endpoint = endpointId == null ? null : endpoints.findById(endpointId)
                .filter(e -> e.getService().getId().equals(serviceId))
                .orElseThrow(() -> NotFoundException.of("Endpoint", endpointId));
        AppUser author = username == null ? null : users.findByUsernameIgnoreCase(username).orElse(null);
        comments.save(new ServiceComment(service, endpoint, author, body.trim()));
        audit.record("COMMENT_ADD", "ApiService", serviceId,
                endpointId == null ? I18n.text("message.comment.on.service") : I18n.text("message.comment.on.endpoint") + endpointId);
    }

    @Transactional
    public void deleteComment(Long serviceId, Long commentId) {
        comments.findById(commentId)
                .filter(c -> c.getService().getId().equals(serviceId))
                .ifPresent(c -> {
                    comments.delete(c);
                    audit.record("COMMENT_DELETE", "ApiService", serviceId, I18n.text("message.comment.removed"));
                });
    }

    @Transactional(readOnly = true)
    public Optional<ApiService> findByGroupAndSlug(Long groupId, String slug) {
        return services.findByGroupIdAndSlugIgnoreCase(groupId, slug);
    }

    @Transactional
    public ApiService createForImport(ApiGroup group, String name, ServiceType type, String version,
                                      String description) {
        String slug = resolveSlug(null, name);
        String candidate = slug;
        int suffix = 2;
        while (services.countSlugInGroup(group.getId(), candidate, null) > 0) {
            candidate = slug + "-" + suffix++;
        }
        ApiService service = new ApiService(group, name, candidate, type);
        service.setVersion(version);
        service.setDescription(description);
        return services.save(service);
    }

    @Transactional
    public ApiService save(ApiService service) {
        return services.save(service);
    }

    private ApiGroup requireGroup(Long groupId) {
        return groups.findById(groupId).orElseThrow(() -> NotFoundException.of(I18n.text("ui.group"), groupId));
    }

    private String resolveSlug(String explicitSlug, String name) {
        String candidate = explicitSlug != null && !explicitSlug.isBlank() ? explicitSlug : name;
        return Slugs.orFallback(candidate, "servizio");
    }

    public static List<String> splitTags(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
