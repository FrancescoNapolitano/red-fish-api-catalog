package it.fn.redfish.catalog.web.admin;

import it.fn.redfish.catalog.support.I18n;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import it.fn.redfish.catalog.service.AuditService;
import it.fn.redfish.catalog.service.FileStorageService;
import it.fn.redfish.catalog.service.SettingsService;
import it.fn.redfish.catalog.support.BusinessException;
import it.fn.redfish.catalog.web.Breadcrumb;

@Controller
@RequestMapping("/admin/settings")
public class SettingsController {

    private final SettingsService settings;
    private final FileStorageService storage;
    private final AuditService audit;

    public SettingsController(SettingsService settings, FileStorageService storage, AuditService audit) {
        this.settings = settings;
        this.storage = storage;
        this.audit = audit;
    }

    @GetMapping
    public String form(Model model) {
        model.addAttribute("settings", settings.snapshot());
        model.addAttribute("appNameValue", settings.appName());
        model.addAttribute("maxUploadKb", settings.maxUploadBytes() / 1024);
        model.addAttribute("apiTestTimeoutSeconds", settings.apiTestTimeout().toSeconds());
        model.addAttribute("healthCheckIntervalMinutes", settings.healthCheckIntervalMinutes());
        model.addAttribute("breadcrumb", List.of(
                Breadcrumb.of(I18n.text("ui.administration"), null),
                Breadcrumb.current(I18n.text("ui.settings"))));
        return "admin/settings";
    }

    @PostMapping
    public String save(@RequestParam String appName,
                       @RequestParam long maxUploadKb,
                       @RequestParam long apiTestTimeoutSeconds,
                       @RequestParam long healthCheckIntervalMinutes,
                       RedirectAttributes flash) {
        if (appName == null || appName.isBlank()) {
            throw new BusinessException(I18n.text("message.the.application.name.is.required"));
        }
        if (maxUploadKb < 16 || maxUploadKb > 20_480) {
            throw new BusinessException(I18n.text("message.the.maximum.upload.size.must.be.between.16.kb.and.20480.kb"));
        }
        if (apiTestTimeoutSeconds < 1 || apiTestTimeoutSeconds > 300) {
            throw new BusinessException(I18n.text("message.the.api.test.timeout.must.be.between.1.and.300.seconds"));
        }
        if (healthCheckIntervalMinutes < 1 || healthCheckIntervalMinutes > 1440) {
            throw new BusinessException(I18n.text("message.the.health.check.interval.must.be.between.1.and.1440.minutes"));
        }

        Map<String, String> values = new LinkedHashMap<>();
        values.put(SettingsService.APP_NAME, appName.trim());
        values.put(SettingsService.UPLOAD_MAX_BYTES, Long.toString(maxUploadKb * 1024));
        values.put(SettingsService.APITEST_TIMEOUT_SECONDS, Long.toString(apiTestTimeoutSeconds));
        values.put(SettingsService.HEALTHCHECK_INTERVAL_MINUTES, Long.toString(healthCheckIntervalMinutes));
        settings.putAll(values);

        audit.record("SETTINGS_UPDATE", "AppSetting", null, I18n.text("message.settings.updated") + values.keySet());
        flash.addFlashAttribute("successMessage", I18n.text("message.settings.saved"));
        return "redirect:/admin/settings";
    }

    @PostMapping("/logo")
    public String uploadLogo(@RequestParam MultipartFile logo, RedirectAttributes flash) {
        String previous = settings.logoPath();
        String path = storage.storeLogo(logo);
        settings.put(SettingsService.APP_LOGO_PATH, path);
        if (previous != null && !previous.isBlank() && !previous.equals(path)) {
            storage.deleteQuietly(previous);
        }
        audit.record("SETTINGS_LOGO", "AppSetting", SettingsService.APP_LOGO_PATH, I18n.text("message.logo.updated") + path);
        flash.addFlashAttribute("successMessage", I18n.text("message.logo.updated.2"));
        return "redirect:/admin/settings";
    }

    @PostMapping("/logo/delete")
    public String deleteLogo(RedirectAttributes flash) {
        String previous = settings.logoPath();
        settings.put(SettingsService.APP_LOGO_PATH, "");
        storage.deleteQuietly(previous);
        audit.record("SETTINGS_LOGO", "AppSetting", SettingsService.APP_LOGO_PATH, I18n.text("message.logo.removed"));
        flash.addFlashAttribute("successMessage", I18n.text("message.logo.removed.2"));
        return "redirect:/admin/settings";
    }

}
