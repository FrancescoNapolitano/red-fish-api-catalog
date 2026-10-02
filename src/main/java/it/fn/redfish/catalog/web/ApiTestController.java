package it.fn.redfish.catalog.web;

import it.fn.redfish.catalog.support.I18n;
import it.fn.redfish.catalog.support.BusinessException;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import it.fn.redfish.catalog.domain.ApiEndpoint;
import it.fn.redfish.catalog.domain.ApiService;
import it.fn.redfish.catalog.domain.Perm;
import it.fn.redfish.catalog.domain.ServiceEnvironment;
import it.fn.redfish.catalog.security.PermissionEvaluator;
import it.fn.redfish.catalog.service.ServiceCatalogService;
import it.fn.redfish.catalog.service.SettingsService;
import it.fn.redfish.catalog.service.ApiUrls;
import it.fn.redfish.catalog.service.SpecVersionService;
import it.fn.redfish.catalog.web.form.ApiTestForm;

@Controller
@RequestMapping("/test")
public class ApiTestController {

    private final ApiTestRunner runner;
    private final ServiceCatalogService catalog;
    private final SpecVersionService specVersions;
    private final SettingsService settings;
    private final PermissionEvaluator perm;

    public ApiTestController(ApiTestRunner runner, ServiceCatalogService catalog, SpecVersionService specVersions,
                             SettingsService settings, PermissionEvaluator perm) {
        this.runner = runner;
        this.catalog = catalog;
        this.specVersions = specVersions;
        this.settings = settings;
        this.perm = perm;
    }

    @GetMapping
    public String console(@RequestParam(required = false) Long serviceId,
                          @RequestParam(required = false) Long endpointId,
                          Model model) {
        perm.require(Perm.API_TEST);

        ApiEndpoint endpoint = endpointId == null ? null : specVersions.endpoint(endpointId);
        ApiTestForm form = new ApiTestForm();
        form.setMethod(endpoint == null ? "GET" : (endpoint.isGrpc() ? "POST" : endpoint.getHttpMethod()));

        prepareConsole(serviceId, endpointId, form, model);
        return "test/console";
    }

    @PostMapping("/execute")
    public String execute(@RequestParam(required = false) Long serviceId,
                          @RequestParam(required = false) Long endpointId,
                          @ModelAttribute("testForm") ApiTestForm form,
                          Model model) {
        perm.require(Perm.API_TEST);

        runner.run(form).addTo(model);
        prepareConsole(serviceId, endpointId, form, model);
        return "test/console";
    }

    private void prepareConsole(Long serviceId, Long endpointId, ApiTestForm form, Model model) {
        ApiEndpoint endpoint = endpointId == null ? null : specVersions.endpoint(endpointId);
        ApiService service = serviceId == null ? (endpoint == null ? null : endpoint.getService()) : catalog.get(serviceId);
        if (endpoint != null && service != null && !endpoint.getService().getId().equals(service.getId())) {
            throw new BusinessException(I18n.text("message.the.endpoint.does.not.belong.to.the.selected.service"));
        }
        List<ServiceEnvironment> environments = service == null ? List.of()
                : catalog.environmentsOf(service.getId());

        if (form.getUrl() == null || form.getUrl().isBlank()) {
            String baseUrl = firstBaseUrl(environments);
            if (endpoint != null) {
                baseUrl = ApiUrls.resolve(specVersions.buildView(endpoint, baseUrl), baseUrl);
            }
            form.setUrl(endpoint == null ? baseUrl : ApiUrls.endpoint(baseUrl, endpoint.getPath()));
        }

        model.addAttribute("service", service);
        model.addAttribute("endpoint", endpoint);
        model.addAttribute("environments", environments);
        model.addAttribute("testForm", form);
        model.addAttribute("timeoutSeconds", settings.apiTestTimeout().toSeconds());
        model.addAttribute("breadcrumb", List.of(Breadcrumb.current(I18n.text("ui.api.test"))));
    }

    private String firstBaseUrl(List<ServiceEnvironment> environments) {
        return environments.stream()
                .map(ServiceEnvironment::getBaseUrl)
                .filter(u -> u != null && !u.isBlank())
                .findFirst()
                .orElse("");
    }
}
