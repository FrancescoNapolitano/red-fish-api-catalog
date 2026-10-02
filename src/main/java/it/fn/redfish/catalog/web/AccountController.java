package it.fn.redfish.catalog.web;

import it.fn.redfish.catalog.support.I18n;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import it.fn.redfish.catalog.security.CatalogUserDetails;
import it.fn.redfish.catalog.security.CatalogUserDetailsService;
import it.fn.redfish.catalog.service.UserService;
import it.fn.redfish.catalog.support.BusinessException;

@Controller
@RequestMapping("/account")
public class AccountController {

    private final UserService userService;
    private final CatalogUserDetailsService userDetailsService;

    public AccountController(UserService userService, CatalogUserDetailsService userDetailsService) {
        this.userService = userService;
        this.userDetailsService = userDetailsService;
    }

    @GetMapping("/password")
    public String passwordForm(Model model) {
        model.addAttribute("breadcrumb", List.of(Breadcrumb.current(I18n.text("ui.change.password"))));
        return "account/password";
    }

    @PostMapping("/password")
    public String changePassword(@AuthenticationPrincipal CatalogUserDetails user,
                                 @RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 RedirectAttributes flash) {
        if (!newPassword.equals(confirmPassword)) {
            throw new BusinessException(I18n.text("message.the.new.passwords.do.not.match"));
        }
        userService.changeOwnPassword(user.getUsername(), currentPassword, newPassword);
        refreshPrincipal(user.getUsername());
        flash.addFlashAttribute("successMessage", I18n.text("message.password.updated"));
        return "redirect:/";
    }

    private void refreshPrincipal(String username) {
        var refreshed = userDetailsService.loadUserByUsername(username);
        var current = SecurityContextHolder.getContext().getAuthentication();
        var authentication = new UsernamePasswordAuthenticationToken(
                refreshed, current == null ? null : current.getCredentials(), refreshed.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
