package it.fn.redfish.catalog.web;

import it.fn.redfish.catalog.support.I18n;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import it.fn.redfish.catalog.domain.ApiGroup;
import it.fn.redfish.catalog.domain.Perm;
import it.fn.redfish.catalog.security.PermissionEvaluator;
import it.fn.redfish.catalog.service.GroupService;
import it.fn.redfish.catalog.service.ServiceCatalogService;
import it.fn.redfish.catalog.web.form.ContactForm;
import it.fn.redfish.catalog.web.form.GroupForm;
import jakarta.validation.Valid;

@Controller
@RequestMapping("/catalog")
public class CatalogController {

    private final GroupService groupService;
    private final ServiceCatalogService serviceCatalog;
    private final PermissionEvaluator perm;

    public CatalogController(GroupService groupService, ServiceCatalogService serviceCatalog,
                             PermissionEvaluator perm) {
        this.groupService = groupService;
        this.serviceCatalog = serviceCatalog;
        this.perm = perm;
    }

    @GetMapping
    public String structure(Model model) {
        perm.require(Perm.CATALOG_VIEW);
        model.addAttribute("tree", groupService.tree());
        model.addAttribute("breadcrumb", List.of(Breadcrumb.current(I18n.text("ui.catalog"))));
        return "catalog/structure";
    }

    @GetMapping("/{id}")
    public String group(@PathVariable Long id, Model model) {
        ApiGroup group = groupService.get(id);
        perm.requireOnGroup(Perm.CATALOG_VIEW, group);

        model.addAttribute("group", group);
        model.addAttribute("children", groupService.findChildren(id));
        model.addAttribute("services", serviceCatalog.findByGroup(id));
        model.addAttribute("contacts", groupService.contactsOfGroup(id));
        model.addAttribute("contactForm", new ContactForm());
        model.addAttribute("activeGroupPath", group.getPath());
        model.addAttribute("breadcrumb", breadcrumbFor(group, null));
        return "catalog/group";
    }

    @GetMapping("/new")
    public String createForm(@RequestParam(required = false) Long parentId, Model model) {
        ApiGroup parent = parentId == null ? null : groupService.get(parentId);
        perm.requireOnGroup(Perm.GROUP_CREATE, parent);

        if (!model.containsAttribute("groupForm")) {
            GroupForm form = new GroupForm();
            form.setParentId(parentId);
            model.addAttribute("groupForm", form);
        }
        model.addAttribute("groupOptions", groupService.options(null));
        model.addAttribute("editing", false);
        model.addAttribute("breadcrumb", parent == null
                ? List.of(Breadcrumb.of(I18n.text("ui.catalog"), "/catalog"), Breadcrumb.current(I18n.text("ui.new.group")))
                : breadcrumbFor(parent, I18n.text("message.new.subgroup")));
        return "catalog/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("groupForm") GroupForm form, BindingResult binding, Model model,
                         RedirectAttributes flash) {
        if (binding.hasErrors()) {
            return createForm(form.getParentId(), model);
        }
        ApiGroup parent = form.getParentId() == null ? null : groupService.get(form.getParentId());
        perm.requireOnGroup(Perm.GROUP_CREATE, parent);
        ApiGroup created = groupService.create(form);
        flash.addFlashAttribute("successMessage", I18n.text("message.group.created") + created.getPath());
        return "redirect:/catalog/" + created.getId();
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        ApiGroup group = groupService.get(id);
        perm.requireOnGroup(Perm.GROUP_EDIT, group);

        if (!model.containsAttribute("groupForm")) {
            model.addAttribute("groupForm", GroupForm.from(group));
        }
        model.addAttribute("group", group);
        model.addAttribute("groupOptions", groupService.options(id));
        model.addAttribute("editing", true);
        model.addAttribute("activeGroupPath", group.getPath());
        model.addAttribute("breadcrumb", breadcrumbFor(group, I18n.text("message.edit")));
        return "catalog/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("groupForm") GroupForm form,
                         BindingResult binding, Model model, RedirectAttributes flash) {
        perm.requireOnGroup(Perm.GROUP_EDIT, groupService.get(id));
        if (binding.hasErrors()) {
            return editForm(id, model);
        }
        ApiGroup updated = groupService.update(id, form);
        flash.addFlashAttribute("successMessage", I18n.text("message.group.updated") + updated.getPath());
        return "redirect:/catalog/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes flash) {
        ApiGroup group = groupService.get(id);
        perm.requireOnGroup(Perm.GROUP_DELETE, group);
        Long parentId = group.getParent() == null ? null : group.getParent().getId();
        groupService.delete(id);
        flash.addFlashAttribute("successMessage", I18n.text("message.group.deleted") + group.getPath());
        return parentId == null ? "redirect:/catalog" : "redirect:/catalog/" + parentId;
    }

    @PostMapping("/{id}/contacts")
    public String addContact(@PathVariable Long id, @Valid @ModelAttribute("contactForm") ContactForm form,
                             BindingResult binding, Model model, RedirectAttributes flash) {
        ApiGroup group = groupService.get(id);
        perm.requireOnGroup(Perm.GROUP_EDIT, group);
        if (binding.hasErrors()) {
            flash.addFlashAttribute("errorMessage", I18n.text("message.invalid.contact.details"));
            return "redirect:/catalog/" + id;
        }
        groupService.addContact(id, form);
        flash.addFlashAttribute("successMessage", I18n.text("message.contact.added"));
        return "redirect:/catalog/" + id;
    }

    @PostMapping("/{id}/contacts/{contactId}/delete")
    public String deleteContact(@PathVariable Long id, @PathVariable Long contactId, RedirectAttributes flash) {
        perm.requireOnGroup(Perm.GROUP_EDIT, groupService.get(id));
        groupService.deleteContact(id, contactId);
        flash.addFlashAttribute("successMessage", I18n.text("message.contact.removed"));
        return "redirect:/catalog/" + id;
    }

    private List<Breadcrumb> breadcrumbFor(ApiGroup group, String trailing) {
        List<Breadcrumb> crumbs = new ArrayList<>();
        crumbs.add(Breadcrumb.of(I18n.text("ui.catalog"), "/catalog"));
        List<ApiGroup> chain = groupService.ancestry(group);
        for (int i = 0; i < chain.size(); i++) {
            ApiGroup g = chain.get(i);
            boolean last = i == chain.size() - 1;
            crumbs.add(last && trailing == null
                    ? Breadcrumb.current(g.getName())
                    : Breadcrumb.of(g.getName(), "/catalog/" + g.getId()));
        }
        if (trailing != null) {
            crumbs.add(Breadcrumb.current(trailing));
        }
        return crumbs;
    }
}
