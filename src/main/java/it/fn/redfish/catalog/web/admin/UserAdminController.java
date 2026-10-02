package it.fn.redfish.catalog.web.admin;

import it.fn.redfish.catalog.support.I18n;
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

import it.fn.redfish.catalog.domain.AppUser;
import it.fn.redfish.catalog.service.GroupService;
import it.fn.redfish.catalog.service.RoleService;
import it.fn.redfish.catalog.service.UserService;
import it.fn.redfish.catalog.web.Breadcrumb;
import it.fn.redfish.catalog.web.form.UserForm;
import jakarta.validation.Valid;

@Controller
@RequestMapping("/admin/users")
public class UserAdminController {

    private final UserService userService;
    private final RoleService roleService;
    private final GroupService groupService;

    public UserAdminController(UserService userService, RoleService roleService, GroupService groupService) {
        this.userService = userService;
        this.roleService = roleService;
        this.groupService = groupService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("users", userService.findAll());
        model.addAttribute("breadcrumb", List.of(
                Breadcrumb.of(I18n.text("ui.administration"), null),
                Breadcrumb.current(I18n.text("ui.users"))));
        return "admin/users/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        if (!model.containsAttribute("userForm")) {
            model.addAttribute("userForm", new UserForm());
        }
        model.addAttribute("roles", roleService.findAll());
        model.addAttribute("suggestedPassword", userService.generatePassword());
        model.addAttribute("breadcrumb", List.of(
                Breadcrumb.of(I18n.text("ui.users"), "/admin/users"),
                Breadcrumb.current(I18n.text("ui.new.user"))));
        return "admin/users/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("userForm") UserForm form, BindingResult binding, Model model,
                         RedirectAttributes flash) {
        if (binding.hasErrors()) {
            return createForm(model);
        }
        AppUser created = userService.create(form);
        flash.addFlashAttribute("successMessage", I18n.text("message.user.2") + created.getUsername() + I18n.text("message.created"));
        return "redirect:/admin/users/" + created.getId();
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        AppUser user = userService.get(id);
        model.addAttribute("user", user);
        if (!model.containsAttribute("userForm")) {
            model.addAttribute("userForm", UserForm.from(user));
        }
        model.addAttribute("roles", roleService.findAll());
        model.addAttribute("groupPermissions", userService.groupPermissions(id));
        model.addAttribute("groupOptions", groupService.options(null));
        model.addAttribute("scopedPermissions", roleService.groupScopedPermissions());
        model.addAttribute("breadcrumb", List.of(
                Breadcrumb.of(I18n.text("ui.users"), "/admin/users"),
                Breadcrumb.current(user.getUsername())));
        return "admin/users/detail";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("userForm") UserForm form,
                         BindingResult binding, Model model, RedirectAttributes flash) {
        if (binding.hasErrors()) {
            return detail(id, model);
        }
        userService.update(id, form);
        flash.addFlashAttribute("successMessage", I18n.text("message.user.updated"));
        return "redirect:/admin/users/" + id;
    }

    @PostMapping("/{id}/enabled")
    public String setEnabled(@PathVariable Long id, @RequestParam boolean enabled, RedirectAttributes flash) {
        userService.setEnabled(id, enabled);
        flash.addFlashAttribute("successMessage", enabled ? I18n.text("message.user.enabled") : I18n.text("message.user.disabled"));
        return "redirect:/admin/users/" + id;
    }

    @PostMapping("/{id}/password")
    public String resetPassword(@PathVariable Long id,
                                @RequestParam(required = false) String newPassword,
                                @RequestParam(defaultValue = "false") boolean mustChange,
                                RedirectAttributes flash) {
        String password = userService.resetPassword(id, newPassword, mustChange);
        boolean generated = newPassword == null || newPassword.isBlank();
        flash.addFlashAttribute(generated ? "infoMessage" : "successMessage", generated
                ? I18n.text("message.generated.password", org.springframework.web.util.HtmlUtils.htmlEscape(password))
                : I18n.text("message.password.reset"));
        return "redirect:/admin/users/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes flash) {
        userService.delete(id);
        flash.addFlashAttribute("successMessage", I18n.text("message.user.deleted"));
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/group-permissions")
    public String addGroupPermission(@PathVariable Long id, @RequestParam Long groupId,
                                     @RequestParam String permissionCode, RedirectAttributes flash) {
        userService.addGroupPermission(id, groupId, permissionCode);
        flash.addFlashAttribute("successMessage", I18n.text("message.group.permission.assigned"));
        return "redirect:/admin/users/" + id;
    }

    @PostMapping("/{id}/group-permissions/{assignmentId}/delete")
    public String removeGroupPermission(@PathVariable Long id, @PathVariable Long assignmentId,
                                        RedirectAttributes flash) {
        userService.removeGroupPermission(id, assignmentId);
        flash.addFlashAttribute("successMessage", I18n.text("message.group.permission.revoked"));
        return "redirect:/admin/users/" + id;
    }
}
