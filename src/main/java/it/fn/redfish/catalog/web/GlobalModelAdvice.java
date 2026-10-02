package it.fn.redfish.catalog.web;

import java.util.List;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import it.fn.redfish.catalog.security.CatalogUserDetails;
import it.fn.redfish.catalog.security.PermissionEvaluator;
import it.fn.redfish.catalog.service.GroupNode;
import it.fn.redfish.catalog.service.GroupService;
import it.fn.redfish.catalog.service.SettingsService;
import jakarta.servlet.http.HttpServletRequest;

@ControllerAdvice(basePackages = "it.fn.redfish.catalog.web")
public class GlobalModelAdvice {

    private final SettingsService settings;
    private final GroupService groupService;
    private final PermissionEvaluator perm;

    public GlobalModelAdvice(SettingsService settings, GroupService groupService, PermissionEvaluator perm) {
        this.settings = settings;
        this.groupService = groupService;
        this.perm = perm;
    }

    @ModelAttribute("appName")
    public String appName() {
        return settings.appName();
    }

    @ModelAttribute("appLogoPath")
    public String appLogoPath() {
        return settings.logoPath();
    }

    @ModelAttribute("currentUser")
    public CatalogUserDetails currentUser() {
        return perm.currentUser().orElse(null);
    }

    @ModelAttribute("requestUri")
    public String requestUri(HttpServletRequest request) {
        return request.getRequestURI();
    }

    @ModelAttribute("sidebarTree")
    public List<GroupNode> sidebarTree() {
        if (perm.currentUser().isEmpty()) {
            return List.of();
        }
        return groupService.tree();
    }
    @ModelAttribute("languageEnUrl")
    public String languageEnUrl(HttpServletRequest request) {
        return languageUrl(request, "en");
    }

    @ModelAttribute("languageItUrl")
    public String languageItUrl(HttpServletRequest request) {
        return languageUrl(request, "it");
    }

    private String languageUrl(HttpServletRequest request, String language) {
        String path = request.getRequestURI();
        String query = request.getQueryString();
        if (!"GET".equals(request.getMethod())) {
            path = request.getContextPath() + "/";
            query = null;
            String referer = request.getHeader("Referer");
            if (referer != null) {
                try {
                    var uri = java.net.URI.create(referer);
                    if (uri.getPath() != null && uri.getPath().startsWith("/") && !uri.getPath().startsWith("//")) {
                        path = uri.getRawPath();
                        query = uri.getRawQuery();
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return org.springframework.web.util.UriComponentsBuilder.fromPath(path).query(query)
                .replaceQueryParam("lang", language).build(true).toUriString();
    }
}
