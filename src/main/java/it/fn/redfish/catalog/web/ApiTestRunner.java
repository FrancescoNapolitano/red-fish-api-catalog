package it.fn.redfish.catalog.web;

import it.fn.redfish.catalog.support.I18n;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import it.fn.redfish.catalog.service.ApiTestService;
import it.fn.redfish.catalog.service.CurlBuilder;
import it.fn.redfish.catalog.service.SpecVersionService;
import it.fn.redfish.catalog.support.BusinessException;
import it.fn.redfish.catalog.web.form.ApiTestForm;

@Component
public class ApiTestRunner {

    private final ApiTestService testService;
    private final SpecVersionService specVersions;
    private final CurlBuilder curlBuilder;

    public ApiTestRunner(ApiTestService testService, SpecVersionService specVersions, CurlBuilder curlBuilder) {
        this.testService = testService;
        this.specVersions = specVersions;
        this.curlBuilder = curlBuilder;
    }

    public record Outcome(ApiTestService.TestResponse response, String curl, String prettyBody) {

        public void addTo(Model model) {
            model.addAttribute("response", response);
            model.addAttribute("curl", curl);
            model.addAttribute("prettyBody", prettyBody);
        }
    }

    public Outcome run(ApiTestForm form) {
        Map<String, String> headers = ApiTestService.parseHeaderLines(form.getHeaders());
        Map<String, String> query = ApiTestService.parseHeaderLines(form.getQueryParams());
        String url = appendQuery(form.getUrl(), query);

        ApiTestService.TestResponse response = testService.execute(
                new ApiTestService.TestRequest(form.getMethod(), url, headers, form.getBody()));

        String curl = curlBuilder.build(form.getMethod(), url, "", Map.of(), headers, form.getBody());
        return new Outcome(response, curl, prettyIfJson(response));
    }

    private String prettyIfJson(ApiTestService.TestResponse response) {
        if (response.body() == null || response.body().isBlank()) {
            return null;
        }
        String contentType = response.contentType() == null ? "" : response.contentType();
        if (!contentType.contains("json")) {
            return null;
        }
        return specVersions.renderer().prettyPrint(response.body());
    }

    private String appendQuery(String url, Map<String, String> query) {
        if (url == null || url.isBlank()) {
            throw new BusinessException(I18n.text("message.the.request.url.is.required"));
        }
        String trimmed = url.trim();
        if (query.isEmpty()) {
            return trimmed;
        }
        String separator = trimmed.contains("?") ? "&" : "?";
        String pairs = query.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .reduce((a, b) -> a + "&" + b)
                .orElse("");
        return trimmed + separator + pairs;
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
