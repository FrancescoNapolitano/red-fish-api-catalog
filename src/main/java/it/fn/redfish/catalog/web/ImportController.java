package it.fn.redfish.catalog.web;

import it.fn.redfish.catalog.support.I18n;
import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import it.fn.redfish.catalog.domain.Perm;
import it.fn.redfish.catalog.security.CatalogUserDetails;
import it.fn.redfish.catalog.security.PermissionEvaluator;
import it.fn.redfish.catalog.service.GroupService;
import it.fn.redfish.catalog.service.ImportService;
import it.fn.redfish.catalog.service.ServiceCatalogService;
import it.fn.redfish.catalog.service.SettingsService;
import it.fn.redfish.catalog.web.form.ImportForm;

@Controller
@RequestMapping("/import")
public class ImportController {

    private final ImportService importService;
    private final GroupService groupService;
    private final ServiceCatalogService catalog;
    private final SettingsService settings;
    private final PermissionEvaluator perm;

    public ImportController(ImportService importService, GroupService groupService, ServiceCatalogService catalog,
                            SettingsService settings, PermissionEvaluator perm) {
        this.importService = importService;
        this.groupService = groupService;
        this.catalog = catalog;
        this.settings = settings;
        this.perm = perm;
    }

    @GetMapping
    public String form(@RequestParam(required = false) Long groupId,
                       @RequestParam(required = false) Long serviceId,
                       Model model) {
        requireAnyImportPermission();
        if (!model.containsAttribute("importForm")) {
            ImportForm form = new ImportForm();
            form.setGroupId(groupId);
            form.setServiceId(serviceId);
            model.addAttribute("importForm", form);
        }
        model.addAttribute("groupOptions", groupService.options(null));
        model.addAttribute("services", catalog.findAll());
        model.addAttribute("maxUploadKb", settings.maxUploadBytes() / 1024);
        model.addAttribute("breadcrumb", List.of(Breadcrumb.current(I18n.text("ui.import.specification"))));
        return "import/form";
    }

    @PostMapping
    public String submit(@ModelAttribute("importForm") ImportForm form,
                         @AuthenticationPrincipal CatalogUserDetails user,
                         RedirectAttributes flash) {
        requireAnyImportPermission();
        ImportService.ImportOutcome outcome = importService.importSpec(form, user == null ? null : user.getUsername());

        String message = (outcome.serviceCreated() ? I18n.text("message.service.created.and.specification.imported") : I18n.text("message.new.revision.imported")) +
                " (r" + outcome.version().getRevision() + "): " +
                outcome.version().getEndpointCount() + " endpoint, " +
                outcome.version().getModelCount() + I18n.text("message.models.2");
        flash.addFlashAttribute("successMessage", message);

        if (!outcome.warnings().isEmpty()) {
            flash.addFlashAttribute("infoMessage", "Avvisi del parser:<br>"
                    + String.join("<br>", outcome.warnings().stream()
                    .map(this::escapeHtml)
                    .limit(10)
                    .toList()));
        }
        return "redirect:/services/" + outcome.service().getId();
    }

    private void requireAnyImportPermission() {
        if (!perm.hasAnywhere(Perm.IMPORT_SWAGGER) && !perm.hasAnywhere(Perm.IMPORT_GRPC)) {
            throw new AccessDeniedException(I18n.text("message.missing.import.permission.import.swagger.or.import.grpc"));
        }
    }

    private String escapeHtml(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
