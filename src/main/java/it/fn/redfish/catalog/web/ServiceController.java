package it.fn.redfish.catalog.web;

import it.fn.redfish.catalog.support.I18n;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import it.fn.redfish.catalog.domain.ApiEndpoint;
import it.fn.redfish.catalog.domain.ApiGroup;
import it.fn.redfish.catalog.domain.ApiService;
import it.fn.redfish.catalog.domain.Perm;
import it.fn.redfish.catalog.domain.ServiceEnvironment;
import it.fn.redfish.catalog.domain.ServiceStatus;
import it.fn.redfish.catalog.domain.ServiceType;
import it.fn.redfish.catalog.domain.SpecFormat;
import it.fn.redfish.catalog.domain.SpecVersion;
import it.fn.redfish.catalog.security.CatalogUserDetails;
import it.fn.redfish.catalog.security.PermissionEvaluator;
import it.fn.redfish.catalog.service.ApiUrls;
import it.fn.redfish.catalog.service.FavoriteService;
import it.fn.redfish.catalog.service.GroupService;
import it.fn.redfish.catalog.service.ImportService;
import it.fn.redfish.catalog.service.ServiceCatalogService;
import it.fn.redfish.catalog.service.SpecDiffService;
import it.fn.redfish.catalog.service.SpecVersionService;
import it.fn.redfish.catalog.spec.EndpointView;
import it.fn.redfish.catalog.support.BusinessException;
import it.fn.redfish.catalog.support.Text;
import it.fn.redfish.catalog.web.form.ApiTestForm;
import it.fn.redfish.catalog.web.form.ContactForm;
import it.fn.redfish.catalog.web.form.ServiceForm;
import jakarta.validation.Valid;

@Controller
@RequestMapping("/services")
public class ServiceController {

    private final ServiceCatalogService catalog;
    private final SpecVersionService specVersions;
    private final SpecDiffService diffService;
    private final ImportService importService;
    private final GroupService groupService;
    private final FavoriteService favorites;
    private final ApiTestRunner testRunner;
    private final ServiceBreadcrumbs breadcrumbs;
    private final PermissionEvaluator perm;

    public ServiceController(ServiceCatalogService catalog, SpecVersionService specVersions,
                             SpecDiffService diffService, ImportService importService, GroupService groupService,
                             FavoriteService favorites, ApiTestRunner testRunner, ServiceBreadcrumbs breadcrumbs,
                             PermissionEvaluator perm) {
        this.catalog = catalog;
        this.specVersions = specVersions;
        this.diffService = diffService;
        this.importService = importService;
        this.groupService = groupService;
        this.favorites = favorites;
        this.testRunner = testRunner;
        this.breadcrumbs = breadcrumbs;
        this.perm = perm;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String type,
                       @RequestParam(required = false) String status,
                       Model model) {
        perm.require(Perm.CATALOG_VIEW);
        List<ApiService> all = catalog.findAll().stream()
                .filter(s -> type == null || type.isBlank() || s.getServiceType().name().equalsIgnoreCase(type))
                .filter(s -> status == null || status.isBlank() || s.getStatus().name().equalsIgnoreCase(status))
                .toList();
        model.addAttribute("services", all);
        model.addAttribute("favoriteIds", favorites.idsFor(perm.currentUserId()));
        model.addAttribute("filterType", type);
        model.addAttribute("filterStatus", status);
        model.addAttribute("breadcrumb", List.of(Breadcrumb.current(I18n.text("ui.services"))));
        return "services/list";
    }

    @GetMapping("/new")
    public String createForm(@RequestParam(required = false) Long groupId, Model model) {
        ApiGroup group = groupId == null ? null : groupService.get(groupId);
        perm.requireOnGroup(Perm.SERVICE_CREATE, group);
        if (!model.containsAttribute("serviceForm")) {
            ServiceForm form = new ServiceForm();
            form.setGroupId(groupId);
            model.addAttribute("serviceForm", form);
        }
        prepareFormModel(model, false);
        model.addAttribute("breadcrumb", List.of(
                Breadcrumb.of(I18n.text("ui.services"), "/services"), Breadcrumb.current(I18n.text("ui.new.service.2"))));
        return "services/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("serviceForm") ServiceForm form, BindingResult binding, Model model,
                         RedirectAttributes flash) {
        if (binding.hasErrors()) {
            return createForm(form.getGroupId(), model);
        }
        perm.requireOnGroup(Perm.SERVICE_CREATE, groupService.get(form.getGroupId()));
        ApiService created = catalog.create(form);
        flash.addFlashAttribute("successMessage", I18n.text("message.service.created"));
        return "redirect:/services/" + created.getId();
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        ApiService service = catalog.get(id);
        perm.requireOnService(Perm.SERVICE_EDIT, service);
        if (!model.containsAttribute("serviceForm")) {
            model.addAttribute("serviceForm", ServiceForm.from(service));
        }
        model.addAttribute("service", service);
        prepareFormModel(model, true);
        model.addAttribute("activeGroupPath", service.getGroup().getPath());
        model.addAttribute("breadcrumb", List.of(
                Breadcrumb.of(I18n.text("ui.services"), "/services"),
                Breadcrumb.of(service.getName(), "/services/" + id),
                Breadcrumb.current(I18n.text("message.edit"))));
        return "services/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("serviceForm") ServiceForm form,
                         BindingResult binding, Model model, RedirectAttributes flash) {
        requireEdit(id);
        if (binding.hasErrors()) {
            return editForm(id, model);
        }
        catalog.update(id, form);
        flash.addFlashAttribute("successMessage", I18n.text("message.service.updated"));
        return "redirect:/services/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes flash) {
        ApiService service = catalog.get(id);
        perm.requireOnService(Perm.SERVICE_DELETE, service);
        Long groupId = service.getGroup().getId();
        catalog.delete(id);
        flash.addFlashAttribute("successMessage", I18n.text("message.service.deleted"));
        return "redirect:/catalog/" + groupId;
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, @RequestParam(required = false) Long version, Model model) {
        ApiService service = catalog.get(id);
        perm.requireOnService(Perm.CATALOG_VIEW, service);

        List<SpecVersion> versions = specVersions.versionsOf(id);
        Optional<SpecVersion> selected = specVersions.resolveVersion(id, version);
        List<ServiceEnvironment> environments = catalog.environmentsOf(id);

        model.addAttribute("service", service);
        model.addAttribute("versions", versions);
        model.addAttribute("selectedVersion", selected.orElse(null));
        model.addAttribute("endpoints", selected.map(v -> specVersions.endpointsOf(v.getId())).orElse(List.of()));
        model.addAttribute("models", selected.map(v -> specVersions.modelsOf(v.getId())).orElse(List.of()));
        model.addAttribute("environments", environments);
        model.addAttribute("standardEnvironments", ServiceCatalogService.STANDARD_ENVIRONMENTS);
        model.addAttribute("links", catalog.linksOf(id));
        model.addAttribute("contacts", catalog.contactsOfService(id));
        model.addAttribute("comments", catalog.commentsOfService(id));
        model.addAttribute("contactForm", new ContactForm());
        model.addAttribute("isFavorite", favorites.isFavorite(perm.currentUserId(), id));
        model.addAttribute("groupChain", groupService.ancestry(service.getGroup()));
        model.addAttribute("activeGroupPath", service.getGroup().getPath());
        model.addAttribute("breadcrumb", breadcrumbs.of(service, null));
        return "services/detail";
    }

    @GetMapping("/{id}/endpoints/{endpointId}")
    public String endpoint(@PathVariable Long id, @PathVariable Long endpointId,
                           @RequestParam(required = false) Long environmentId, Model model) {
        ApiService service = catalog.get(id);
        perm.requireOnService(Perm.CATALOG_VIEW, service);
        ApiEndpoint endpoint = specVersions.endpoint(endpointId);
        if (!endpoint.getService().getId().equals(id)) {
            throw new BusinessException(I18n.text("message.the.endpoint.does.not.belong.to.the.selected.service"));
        }

        List<ServiceEnvironment> environments = catalog.environmentsOf(id);
        String baseUrl = resolveBaseUrl(environments, environmentId);
        EndpointView view = specVersions.buildView(endpoint, baseUrl);
        baseUrl = ApiUrls.resolve(view, baseUrl);

        if (!model.containsAttribute("testForm")) {
            model.addAttribute("testForm", prefilledTestForm(endpoint, view, baseUrl));
        }

        model.addAttribute("service", service);
        model.addAttribute("endpoint", endpoint);
        model.addAttribute("view", view);
        model.addAttribute("selectedVersion", endpoint.getSpecVersion());
        model.addAttribute("environments", environments);
        model.addAttribute("selectedEnvironmentId", environmentId);
        model.addAttribute("baseUrl", baseUrl);
        model.addAttribute("comments", catalog.commentsOfEndpoint(endpointId));
        model.addAttribute("siblingEndpoints", specVersions.endpointsOf(endpoint.getSpecVersion().getId()));
        model.addAttribute("activeGroupPath", service.getGroup().getPath());
        model.addAttribute("breadcrumb", breadcrumbs.of(service,
                endpoint.getHttpMethod() + " " + endpoint.getPath()));
        return "services/endpoint";
    }

    @PostMapping("/{id}/endpoints/{endpointId}/test")
    public String testEndpoint(@PathVariable Long id, @PathVariable Long endpointId,
                               @RequestParam(required = false) Long environmentId,
                               @ModelAttribute("testForm") ApiTestForm form, Model model) {
        perm.require(Perm.API_TEST);
        testRunner.run(form).addTo(model);
        return endpoint(id, endpointId, environmentId, model);
    }

    private ApiTestForm prefilledTestForm(ApiEndpoint endpoint, EndpointView view, String baseUrl) {
        ApiTestForm form = new ApiTestForm();
        form.setMethod(endpoint.isGrpc() ? "POST" : endpoint.getHttpMethod());
        form.setUrl(ApiUrls.endpoint(baseUrl, endpoint.getPath()));
        form.setHeaders(view.defaultContentType() == null ? ""
                : "Content-Type: " + view.defaultContentType());
        form.setBody(view.defaultBodySample());
        return form;
    }

    @GetMapping("/{id}/versions")
    public String versions(@PathVariable Long id, Model model) {
        ApiService service = catalog.get(id);
        perm.requireOnService(Perm.CATALOG_VIEW, service);
        model.addAttribute("service", service);
        model.addAttribute("versions", specVersions.versionsOf(id));
        model.addAttribute("activeGroupPath", service.getGroup().getPath());
        model.addAttribute("breadcrumb", breadcrumbs.of(service, I18n.text("ui.revisions")));
        return "services/versions";
    }

    @PostMapping("/{id}/versions/{versionId}/current")
    public String makeCurrent(@PathVariable Long id, @PathVariable Long versionId, RedirectAttributes flash) {
        requireEdit(id);
        importService.makeCurrent(id, versionId);
        flash.addFlashAttribute("successMessage", I18n.text("message.revision.set.as.current"));
        return "redirect:/services/" + id + "/versions";
    }

    @PostMapping("/{id}/versions/{versionId}/delete")
    public String deleteVersion(@PathVariable Long id, @PathVariable Long versionId, RedirectAttributes flash) {
        perm.requireOnService(Perm.SERVICE_DELETE, catalog.get(id));
        importService.deleteVersion(id, versionId);
        flash.addFlashAttribute("successMessage", I18n.text("message.revision.deleted"));
        return "redirect:/services/" + id + "/versions";
    }

    @GetMapping("/{id}/diff")
    public String diff(@PathVariable Long id,
                       @RequestParam(required = false) Long from,
                       @RequestParam(required = false) Long to,
                       Model model) {
        ApiService service = catalog.get(id);
        perm.requireOnService(Perm.CATALOG_VIEW, service);
        List<SpecVersion> versions = specVersions.versionsOf(id);

        Long resolvedTo = to != null ? to : versions.stream().findFirst().map(SpecVersion::getId).orElse(null);
        Long resolvedFrom = from != null ? from
                : versions.stream().skip(1).findFirst().map(SpecVersion::getId).orElse(null);

        model.addAttribute("service", service);
        model.addAttribute("versions", versions);
        model.addAttribute("fromId", resolvedFrom);
        model.addAttribute("toId", resolvedTo);
        if (resolvedFrom != null && resolvedTo != null && !resolvedFrom.equals(resolvedTo)) {
            model.addAttribute("diff", diffService.diff(id, resolvedFrom, resolvedTo));
        }
        model.addAttribute("activeGroupPath", service.getGroup().getPath());
        model.addAttribute("breadcrumb", breadcrumbs.of(service, I18n.text("ui.compare.revisions")));
        return "services/diff";
    }

    @GetMapping("/{id}/versions/{versionId}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id, @PathVariable Long versionId) {
        ApiService service = catalog.get(id);
        perm.requireOnService(Perm.SPEC_DOWNLOAD, service);
        SpecVersion version = specVersions.version(id, versionId);

        byte[] body = version.getRawContent().getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaTypeOf(version));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(version.downloadFileName(), StandardCharsets.UTF_8).build());
        headers.setContentLength(body.length);
        return ResponseEntity.ok().headers(headers).body(body);
    }

    @GetMapping("/{id}/versions/{versionId}/raw")
    @ResponseBody
    public ResponseEntity<String> raw(@PathVariable Long id, @PathVariable Long versionId) {
        ApiService service = catalog.get(id);
        perm.requireOnService(Perm.SPEC_DOWNLOAD, service);
        SpecVersion version = specVersions.version(id, versionId);
        return ResponseEntity.ok()
                .contentType(new MediaType(mediaTypeOf(version), StandardCharsets.UTF_8))
                .body(version.getRawContent());
    }

    private MediaType mediaTypeOf(SpecVersion version) {
        if (version.getSpecFormat() == SpecFormat.PROTO) {
            return MediaType.TEXT_PLAIN;
        }
        char first = Text.firstNonWhitespace(version.getRawContent());
        return first == '{' || first == '['
                ? MediaType.APPLICATION_JSON
                : MediaType.parseMediaType("application/yaml");
    }

    @PostMapping("/{id}/environments")
    public String saveEnvironment(@PathVariable Long id, @RequestParam String name,
                                  @RequestParam(required = false) String baseUrl,
                                  @RequestParam(required = false) String healthCheckUrl,
                                  @RequestParam(required = false) String specUrl,
                                  RedirectAttributes flash) {
        requireEdit(id);
        catalog.saveEnvironment(id, name, baseUrl, healthCheckUrl, specUrl);
        flash.addFlashAttribute("successMessage", I18n.text("message.environment.saved"));
        return "redirect:/services/" + id;
    }

    @PostMapping("/{id}/environments/{environmentId}/delete")
    public String deleteEnvironment(@PathVariable Long id, @PathVariable Long environmentId,
                                    RedirectAttributes flash) {
        requireEdit(id);
        catalog.deleteEnvironment(id, environmentId);
        flash.addFlashAttribute("successMessage", I18n.text("message.environment.removed"));
        return "redirect:/services/" + id;
    }

    @PostMapping("/{id}/links")
    public String addLink(@PathVariable Long id, @RequestParam String label, @RequestParam String url,
                          @RequestParam(required = false) String kind, RedirectAttributes flash) {
        requireEdit(id);
        catalog.addLink(id, label, url, kind);
        flash.addFlashAttribute("successMessage", I18n.text("message.link.added"));
        return "redirect:/services/" + id;
    }

    @PostMapping("/{id}/links/{linkId}/delete")
    public String deleteLink(@PathVariable Long id, @PathVariable Long linkId, RedirectAttributes flash) {
        requireEdit(id);
        catalog.deleteLink(id, linkId);
        flash.addFlashAttribute("successMessage", I18n.text("message.link.removed"));
        return "redirect:/services/" + id;
    }

    @PostMapping("/{id}/contacts")
    public String addContact(@PathVariable Long id, @Valid @ModelAttribute("contactForm") ContactForm form,
                             BindingResult binding, RedirectAttributes flash) {
        requireEdit(id);
        if (binding.hasErrors()) {
            flash.addFlashAttribute("errorMessage", I18n.text("message.invalid.contact.details"));
            return "redirect:/services/" + id;
        }
        catalog.addContact(id, form);
        flash.addFlashAttribute("successMessage", I18n.text("message.contact.added"));
        return "redirect:/services/" + id;
    }

    @PostMapping("/{id}/contacts/{contactId}/delete")
    public String deleteContact(@PathVariable Long id, @PathVariable Long contactId, RedirectAttributes flash) {
        requireEdit(id);
        catalog.deleteContact(id, contactId);
        flash.addFlashAttribute("successMessage", I18n.text("message.contact.removed"));
        return "redirect:/services/" + id;
    }

    @PostMapping("/{id}/comments")
    public String addComment(@PathVariable Long id, @RequestParam(required = false) Long endpointId,
                             @RequestParam String body, @AuthenticationPrincipal CatalogUserDetails user,
                             RedirectAttributes flash) {
        perm.requireOnService(Perm.COMMENT_WRITE, catalog.get(id));
        catalog.addComment(id, endpointId, user == null ? null : user.getUsername(), body);
        flash.addFlashAttribute("successMessage", I18n.text("message.comment.added"));
        return endpointId == null
                ? "redirect:/services/" + id
                : "redirect:/services/" + id + "/endpoints/" + endpointId;
    }

    @PostMapping("/{id}/comments/{commentId}/delete")
    public String deleteComment(@PathVariable Long id, @PathVariable Long commentId,
                                @RequestParam(required = false) Long endpointId, RedirectAttributes flash) {
        requireEdit(id);
        catalog.deleteComment(id, commentId);
        flash.addFlashAttribute("successMessage", I18n.text("message.comment.removed.2"));
        return endpointId == null
                ? "redirect:/services/" + id
                : "redirect:/services/" + id + "/endpoints/" + endpointId;
    }

    @PostMapping("/{id}/favorite")
    public String toggleFavorite(@PathVariable Long id, RedirectAttributes flash) {
        Long userId = perm.currentUserId();
        if (userId == null) {
            throw new BusinessException(I18n.text("message.user.not.identified"));
        }
        boolean added = favorites.toggle(userId, id);
        flash.addFlashAttribute("successMessage", added ? I18n.text("message.added.to.favorites") : I18n.text("message.removed.from.favorites"));
        return "redirect:/services/" + id;
    }

    private void requireEdit(Long id) {
        perm.requireOnService(Perm.SERVICE_EDIT, catalog.get(id));
    }

    private String resolveBaseUrl(List<ServiceEnvironment> environments, Long environmentId) {
        if (environmentId != null) {
            return environments.stream()
                    .filter(e -> e.getId().equals(environmentId))
                    .map(ServiceEnvironment::getBaseUrl)
                    .filter(url -> url != null && !url.isBlank())
                    .findFirst()
                    .orElse("");
        }
        return specVersions.suggestedBaseUrl(environments);
    }

    private void prepareFormModel(Model model, boolean editing) {
        model.addAttribute("groupOptions", groupService.options(null));
        model.addAttribute("serviceTypes", ServiceType.values());
        model.addAttribute("serviceStatuses", ServiceStatus.values());
        model.addAttribute("allTags", catalog.allTags());
        model.addAttribute("editing", editing);
    }

}
