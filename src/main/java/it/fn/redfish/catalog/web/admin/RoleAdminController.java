package it.fn.redfish.catalog.web.admin;

import it.fn.redfish.catalog.support.I18n;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import it.fn.redfish.catalog.service.RoleService;
import it.fn.redfish.catalog.web.Breadcrumb;

@Controller
@RequestMapping("/admin/roles")
public class RoleAdminController {

    private final RoleService roleService;

    public RoleAdminController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("roles", roleService.findAll());
        model.addAttribute("permissionsByCategory", roleService.permissionsByCategory());
        model.addAttribute("allPermissions", roleService.allPermissions());
        model.addAttribute("breadcrumb", List.of(
                Breadcrumb.of(I18n.text("ui.administration"), null),
                Breadcrumb.current(I18n.text("ui.roles.and.permissions"))));
        return "admin/roles/list";
    }

    @PostMapping
    public String create(@RequestParam String name,
                         @RequestParam(required = false) String description,
                         @RequestParam(required = false) Set<String> permissions,
                         RedirectAttributes flash) {
        roleService.create(name, description, permissions == null ? new LinkedHashSet<>() : permissions);
        flash.addFlashAttribute("successMessage", I18n.text("message.role.created"));
        return "redirect:/admin/roles";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @RequestParam(required = false) String description,
                         @RequestParam(required = false) Set<String> permissions,
                         RedirectAttributes flash) {
        roleService.updatePermissions(id, description, permissions == null ? new LinkedHashSet<>() : permissions);
        flash.addFlashAttribute("successMessage", I18n.text("message.role.permissions.updated"));
        return "redirect:/admin/roles";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes flash) {
        roleService.delete(id);
        flash.addFlashAttribute("successMessage", I18n.text("message.role.deleted"));
        return "redirect:/admin/roles";
    }
}
