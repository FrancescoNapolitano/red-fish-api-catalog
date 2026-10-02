package it.fn.redfish.catalog.web;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import it.fn.redfish.catalog.security.PermissionEvaluator;
import it.fn.redfish.catalog.service.DashboardService;
import it.fn.redfish.catalog.service.FavoriteService;

@Controller
public class HomeController {

    private final DashboardService dashboard;
    private final FavoriteService favorites;
    private final PermissionEvaluator perm;

    public HomeController(DashboardService dashboard, FavoriteService favorites, PermissionEvaluator perm) {
        this.dashboard = dashboard;
        this.favorites = favorites;
        this.perm = perm;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("counters", dashboard.counters());
        model.addAttribute("recentImports", dashboard.recentImports(8));
        model.addAttribute("recentlyUpdated", dashboard.recentlyUpdated(8));
        model.addAttribute("unhealthy", dashboard.unhealthyEnvironments());
        model.addAttribute("withoutContacts", dashboard.withoutContacts());
        model.addAttribute("withoutEnvironmentUrl", dashboard.withoutEnvironmentUrl());
        Long userId = perm.currentUserId();
        model.addAttribute("myFavorites", userId == null ? List.of() : favorites.listFor(userId));
        model.addAttribute("breadcrumb", List.of(Breadcrumb.current("Dashboard")));
        return "dashboard";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
