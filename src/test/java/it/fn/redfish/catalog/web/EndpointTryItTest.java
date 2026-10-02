package it.fn.redfish.catalog.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import it.fn.redfish.catalog.domain.ApiEndpoint;
import it.fn.redfish.catalog.domain.ApiService;
import it.fn.redfish.catalog.security.PermissionEvaluator;
import it.fn.redfish.catalog.service.ApiTestService;
import it.fn.redfish.catalog.service.FavoriteService;
import it.fn.redfish.catalog.service.GroupService;
import it.fn.redfish.catalog.service.ImportService;
import it.fn.redfish.catalog.service.ServiceCatalogService;
import it.fn.redfish.catalog.service.SettingsService;
import it.fn.redfish.catalog.service.SpecDiffService;
import it.fn.redfish.catalog.service.SpecVersionService;
import it.fn.redfish.catalog.spec.EndpointView;

@WebMvcTest(ServiceController.class)
@WithMockUser
class EndpointTryItTest {

    private static final long SERVICE_ID = 3L;
    private static final long ENDPOINT_ID = 55L;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ServiceCatalogService catalog;
    @MockitoBean
    private SpecVersionService specVersions;
    @MockitoBean
    private SpecDiffService diffService;
    @MockitoBean
    private ImportService importService;
    @MockitoBean
    private GroupService groupService;
    @MockitoBean
    private FavoriteService favorites;
    @MockitoBean
    private ApiTestRunner testRunner;
    @MockitoBean
    private ServiceBreadcrumbs breadcrumbs;
    @MockitoBean
    private SettingsService settings;
    @MockitoBean(name = "perm")
    private PermissionEvaluator perm;

    @BeforeEach
    void stub() {
        when(settings.isSetupCompleted()).thenReturn(true);
        when(settings.appName()).thenReturn("API Catalog");
        when(settings.logoPath()).thenReturn("");
        when(perm.currentUser()).thenReturn(Optional.empty());
        when(perm.has(any())).thenReturn(true);
        when(breadcrumbs.of(any(), any())).thenReturn(List.of());

        ApiService service = mock(ApiService.class, RETURNS_DEEP_STUBS);
        when(service.getId()).thenReturn(SERVICE_ID);
        when(service.getName()).thenReturn("Pagamenti");
        when(service.getGroup().getPath()).thenReturn("/finance/payments");
        when(service.getGroup().getName()).thenReturn("Payments");
        when(catalog.get(SERVICE_ID)).thenReturn(service);
        when(catalog.environmentsOf(anyLong())).thenReturn(List.of());
        when(catalog.commentsOfEndpoint(anyLong())).thenReturn(List.of());

        ApiEndpoint endpoint = mock(ApiEndpoint.class, RETURNS_DEEP_STUBS);
        when(endpoint.getId()).thenReturn(ENDPOINT_ID);
        when(endpoint.getHttpMethod()).thenReturn("GET");
        when(endpoint.getPath()).thenReturn("/transfers");
        when(endpoint.isGrpc()).thenReturn(false);
        when(endpoint.getService().getId()).thenReturn(SERVICE_ID);
        when(specVersions.endpoint(ENDPOINT_ID)).thenReturn(endpoint);
        when(specVersions.endpointsOf(any())).thenReturn(List.of());

        EndpointView view = mock(EndpointView.class, RETURNS_DEEP_STUBS);
        when(view.defaultContentType()).thenReturn("application/json");
        when(view.getCurl()).thenReturn("curl https://api.example.com/transfers");
        when(view.defaultBodySample()).thenReturn("{ }");
        when(specVersions.buildView(any(), any())).thenReturn(view);
    }

    @Test
    @DisplayName("apertura della scheda: modulo precompilato dalla specifica, nessun esito")
    void schedaEndpoint_precompilaIlModulo() throws Exception {
        String body = mvc.perform(get("/services/{id}/endpoints/{eid}", SERVICE_ID, ENDPOINT_ID).param("lang", "it"))
                .andExpect(status().isOk())
                .andExpect(view().name("services/endpoint"))
                .andReturn().getResponse().getContentAsString();

        Assertions.assertTrue(body.contains("Content-Type: application/json"),
                "gli headers di partenza vengono dalla specifica");
        Assertions.assertTrue(body.contains("/transfers"), "l'URL di partenza contiene il path");
        Assertions.assertFalse(body.contains("id=\"test-result\""),
                "senza esecuzione non deve comparire il riquadro dell'esito");
    }

    @Test
    @DisplayName("esecuzione: la scheda torna con l'esito della chiamata")
    void tryIt_rendeEsitoNellaScheda() throws Exception {
        ApiTestService.TestResponse response = new ApiTestService.TestResponse(
                201, "Created", 17L, "{}", "application/json",
                List.of(Map.entry("Location", "/transfers/1")), 2L, false, null, null);
        when(testRunner.run(any())).thenReturn(new ApiTestRunner.Outcome(response, "curl ...", null));

        String body = mvc.perform(post("/services/{id}/endpoints/{eid}/test", SERVICE_ID, ENDPOINT_ID)
                        .with(csrf())
                        .param("method", "POST")
                        .param("url", "https://api.example.com/transfers")
                        .param("headers", "Authorization: Bearer xyz")
                        .param("body", "{\"importo\":10}")
                        .param("queryParams", "").param("lang", "it"))
                .andExpect(status().isOk())
                .andExpect(view().name("services/endpoint"))
                .andReturn().getResponse().getContentAsString();

        Assertions.assertTrue(body.contains("201 Created"), "manca l'esito");
        Assertions.assertTrue(body.contains("17 ms"), "manca la durata");

        Assertions.assertTrue(body.contains("Authorization: Bearer xyz"), "headers non conservati");
        Assertions.assertFalse(body.contains("Content-Type: application/json"),
                "il modulo è tornato ai valori di partenza invece di conservare i dati digitati");

        Assertions.assertTrue(body.contains("data-panel-keep-open"),
                "l'esito non è marcato: resterebbe nascosto in un pannello chiuso");
    }

    @Test
    @DisplayName("le card della scheda sono richiudibili, e partono tutte aperte")
    void schedaEndpoint_cardRichiudibili() throws Exception {
        String body = mvc.perform(get("/services/{id}/endpoints/{eid}", SERVICE_ID, ENDPOINT_ID).param("lang", "it"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        for (String key : List.of("request-body", "responses", "security", "curl", "test", "comments")) {
            Assertions.assertTrue(body.contains("data-panel=\"" + key + "\""),
                    "il pannello '" + key + "' non è richiudibile");
        }

        Assertions.assertTrue(body.contains("class=\"rf-panel-header rf-panel-toggle\""),
                "la testata non è un comando");
        Assertions.assertTrue(body.contains("aria-expanded=\"true\""),
                "le card devono partire aperte");
        Assertions.assertFalse(body.contains("rf-panel-collapsed"),
                "nessuna card deve essere chiusa lato server");
    }
    @Test
    void mostraIRischiNelConfrontoRevisioni() throws Exception {
        var from = mock(it.fn.redfish.catalog.domain.SpecVersion.class);
        var to = mock(it.fn.redfish.catalog.domain.SpecVersion.class);
        when(from.getId()).thenReturn(10L);
        when(to.getId()).thenReturn(20L);
        when(from.getDisplayLabel()).thenReturn("v1");
        when(to.getDisplayLabel()).thenReturn("v2");
        when(specVersions.versionsOf(SERVICE_ID)).thenReturn(List.of(to, from));
        when(diffService.diff(SERVICE_ID, 10L, 20L)).thenReturn(new SpecDiffService.SpecDiff(
                from, to, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(new SpecDiffService.CompatibilityRisk("GET /transfers", "Endpoint rimosso"))));
        String html = mvc.perform(get("/services/{id}/diff", SERVICE_ID).param("lang", "it"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(html)
                .contains("Modifiche potenzialmente incompatibili", "Endpoint rimosso");
    }

    @Test
    void ambienteSelezionatoPrevaleSulServerDellaSpecifica() throws Exception {
        var environment = mock(it.fn.redfish.catalog.domain.ServiceEnvironment.class);
        when(environment.getId()).thenReturn(9L);
        when(environment.getName()).thenReturn("TEST");
        when(environment.getBaseUrl()).thenReturn("https://test.example.com/v2/");
        when(catalog.environmentsOf(SERVICE_ID)).thenReturn(List.of(environment));
        var endpoint = specVersions.endpoint(ENDPOINT_ID);
        var endpointView = new EndpointView(endpoint);
        endpointView.getServers().add(new EndpointView.ServerView("https://prod.example.com/v1", null));
        endpointView.setCurl(new it.fn.redfish.catalog.service.CurlBuilder()
                .fromEndpoint(endpointView, environment.getBaseUrl()));
        when(specVersions.buildView(endpoint, environment.getBaseUrl())).thenReturn(endpointView);
        var result = mvc.perform(get("/services/{id}/endpoints/{eid}", SERVICE_ID, ENDPOINT_ID)
                        .param("environmentId", "9").param("lang", "it"))
                .andExpect(status().isOk()).andReturn();
        var form = (it.fn.redfish.catalog.web.form.ApiTestForm) result.getModelAndView().getModel().get("testForm");
        org.assertj.core.api.Assertions.assertThat(form.getUrl()).isEqualTo("https://test.example.com/v2/transfers");
        org.assertj.core.api.Assertions.assertThat(endpointView.getCurl()).contains(form.getUrl());
    }
}
