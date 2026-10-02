package it.fn.redfish.catalog.web.admin;

import it.fn.redfish.catalog.support.I18n;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import it.fn.redfish.catalog.service.AuditService;
import it.fn.redfish.catalog.web.Breadcrumb;

@Controller
@RequestMapping("/admin/audit")
public class AuditController {

    private static final int PAGE_SIZE = 50;

    private final AuditService audit;

    public AuditController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page, Model model) {
        var result = audit.page(page, PAGE_SIZE);
        model.addAttribute("entries", result.getContent());
        model.addAttribute("page", result.getNumber());
        model.addAttribute("totalPages", result.getTotalPages());
        model.addAttribute("totalElements", result.getTotalElements());
        model.addAttribute("breadcrumb", List.of(
                Breadcrumb.of(I18n.text("ui.administration"), null),
                Breadcrumb.current("Audit log")));
        return "admin/audit";
    }
}
