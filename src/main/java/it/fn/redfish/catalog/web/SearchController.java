package it.fn.redfish.catalog.web;

import it.fn.redfish.catalog.support.I18n;
import it.fn.redfish.catalog.domain.ServiceType;
import it.fn.redfish.catalog.domain.ServiceStatus;
import it.fn.redfish.catalog.service.GroupService;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import it.fn.redfish.catalog.domain.Perm;
import it.fn.redfish.catalog.security.PermissionEvaluator;
import it.fn.redfish.catalog.service.FavoriteService;
import it.fn.redfish.catalog.service.SearchService;

@Controller
public class SearchController {

    private final SearchService searchService;
    private final FavoriteService favorites;
    private final PermissionEvaluator perm;
    private final GroupService groups;

    public SearchController(SearchService searchService, FavoriteService favorites, PermissionEvaluator perm,
                            GroupService groups) {
        this.searchService = searchService;
        this.favorites = favorites;
        this.perm = perm;
        this.groups = groups;
    }

    @GetMapping("/search")
    public String search(@RequestParam(required = false) String q,
                         @RequestParam(required = false) Long groupId,
                         @RequestParam(required = false) ServiceType type,
                         @RequestParam(required = false) ServiceStatus status,
                         @RequestParam(defaultValue = "0") int page, Model model) {
        perm.require(Perm.CATALOG_VIEW);
        String groupPath = groupId == null ? null : groups.get(groupId).getPath();
        model.addAttribute("results", searchService.search(q, groupPath, type, status, page));
        model.addAttribute("groupOptions", groups.options(null));
        model.addAttribute("filterGroup", groupId);
        model.addAttribute("filterType", type);
        model.addAttribute("filterStatus", status);
        model.addAttribute("serviceTypes", ServiceType.values());
        model.addAttribute("serviceStatuses", ServiceStatus.values());
        model.addAttribute("query", q);
        model.addAttribute("breadcrumb", List.of(Breadcrumb.current(I18n.text("ui.search"))));
        return "search";
    }

    @GetMapping("/favorites")
    public String favorites(Model model) {
        Long userId = perm.currentUserId();
        model.addAttribute("services", userId == null ? List.of() : favorites.listFor(userId));
        model.addAttribute("breadcrumb", List.of(Breadcrumb.current(I18n.text("ui.favorites"))));
        return "favorites";
    }
}
