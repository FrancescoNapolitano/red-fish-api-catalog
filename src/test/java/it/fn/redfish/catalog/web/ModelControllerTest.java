package it.fn.redfish.catalog.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;
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
import it.fn.redfish.catalog.domain.ApiModel;
import it.fn.redfish.catalog.domain.ApiService;
import it.fn.redfish.catalog.domain.ModelKind;
import it.fn.redfish.catalog.domain.SpecVersion;
import it.fn.redfish.catalog.security.PermissionEvaluator;
import it.fn.redfish.catalog.service.GroupService;
import it.fn.redfish.catalog.service.ServiceCatalogService;
import it.fn.redfish.catalog.service.SettingsService;
import it.fn.redfish.catalog.service.SpecVersionService;
import it.fn.redfish.catalog.spec.SchemaRenderer;

@WebMvcTest(ModelController.class)
@WithMockUser
class ModelControllerTest {

    private static final long SERVICE_ID = 4L;
    private static final long VERSION_ID = 9L;
    private static final long ENDPOINT_ID = 77L;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ServiceCatalogService catalog;
    @MockitoBean
    private SpecVersionService specVersions;
    @MockitoBean
    private ServiceBreadcrumbs breadcrumbs;
    @MockitoBean
    private SettingsService settings;
    @MockitoBean
    private GroupService groupService;
    @MockitoBean(name = "perm")
    private PermissionEvaluator perm;

    private ApiModel transfer;
    private ApiModel party;
    private ApiModel scollegato;

    @BeforeEach
    void stub() {
        when(settings.isSetupCompleted()).thenReturn(true);
        when(settings.appName()).thenReturn("API Catalog");
        when(settings.logoPath()).thenReturn("");
        when(perm.currentUser()).thenReturn(Optional.empty());
        when(breadcrumbs.of(any(), any())).thenReturn(List.of());

        when(specVersions.renderer()).thenReturn(new SchemaRenderer());

        ApiService service = mock(ApiService.class, RETURNS_DEEP_STUBS);
        when(service.getId()).thenReturn(SERVICE_ID);
        when(service.getName()).thenReturn("Pagamenti");
        when(service.getGroup().getPath()).thenReturn("/finance");
        when(catalog.get(SERVICE_ID)).thenReturn(service);

        SpecVersion version = mock(SpecVersion.class, RETURNS_DEEP_STUBS);
        when(version.getId()).thenReturn(VERSION_ID);
        when(version.getDisplayLabel()).thenReturn("rev. 3");
        when(specVersions.resolveVersion(anyLong(), any())).thenReturn(Optional.of(version));

        transfer = model(1L, "Transfer", ModelKind.SCHEMA, service, version, """
                {"properties":{"debtor":{"$ref":"#/components/schemas/Party"},
                               "amount":{"type":"number","format":"double"}}}
                """);
        party = model(2L, "Party", ModelKind.SCHEMA, service, version,
                "{\"properties\":{\"iban\":{\"type\":\"string\"}}}");
        scollegato = model(3L, "Audit", ModelKind.SCHEMA, service, version, "{\"type\":\"object\"}");

        when(specVersions.modelsOf(VERSION_ID)).thenReturn(List.of(transfer, party, scollegato));
    }

    private ApiModel model(long id, String name, ModelKind kind, ApiService service, SpecVersion version,
                           String schemaJson) {
        ApiModel m = mock(ApiModel.class);
        when(m.getId()).thenReturn(id);
        when(m.getName()).thenReturn(name);
        when(m.getKind()).thenReturn(kind);
        when(m.getSchemaJson()).thenReturn(schemaJson);
        when(m.getService()).thenReturn(service);
        when(m.getSpecVersion()).thenReturn(version);
        return m;
    }

    @Test
    @DisplayName("elenco: tutti i modelli della revisione, ciascuno apribile")
    void elenco_mostraTuttiIModelli() throws Exception {
        String body = mvc.perform(get("/services/{id}/models", SERVICE_ID).param("lang", "it"))
                .andExpect(status().isOk())
                .andExpect(view().name("services/models"))
                .andReturn().getResponse().getContentAsString();

        Assertions.assertTrue(body.contains("Transfer"), "manca Transfer");
        Assertions.assertTrue(body.contains("Party"), "manca Party");
        Assertions.assertTrue(body.contains("Audit"), "manca Audit");
        Assertions.assertTrue(body.contains("/services/4/models/1"), "il modello non è cliccabile");
    }

    @Test
    @DisplayName("elenco filtrato: solo i modelli che l'endpoint usa, riferimenti annidati inclusi")
    void elenco_filtratoPerEndpoint() throws Exception {
        ApiEndpoint endpoint = mock(ApiEndpoint.class, RETURNS_DEEP_STUBS);
        when(endpoint.getId()).thenReturn(ENDPOINT_ID);
        when(endpoint.getHttpMethod()).thenReturn("POST");
        when(endpoint.getPath()).thenReturn("/transfers");
        when(endpoint.getService().getId()).thenReturn(SERVICE_ID);
        when(endpoint.getRequestBodyJson()).thenReturn(
                "{\"content\":{\"application/json\":{\"schema\":{\"$ref\":\"#/components/schemas/Transfer\"}}}}");
        when(endpoint.getResponsesJson()).thenReturn("{}");
        when(specVersions.endpoint(ENDPOINT_ID)).thenReturn(endpoint);

        String body = mvc.perform(get("/services/{id}/models", SERVICE_ID).param("endpointId", "77").param("lang", "it"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Assertions.assertTrue(body.contains("Transfer"), "manca il modello citato direttamente");
        Assertions.assertTrue(body.contains("Party"), "manca il modello citato da Transfer");
        Assertions.assertFalse(body.contains(">Audit<"), "un modello non usato non va elencato");
    }

    @Test
    @DisplayName("scheda modello: struttura, esempio, schema originale e modelli collegati")
    void schedaModello() throws Exception {
        when(specVersions.model(1L)).thenReturn(transfer);

        String body = mvc.perform(get("/services/{sid}/models/{mid}", SERVICE_ID, 1L).param("lang", "it"))
                .andExpect(status().isOk())
                .andExpect(view().name("services/model"))
                .andReturn().getResponse().getContentAsString();

        Assertions.assertTrue(body.contains("Transfer"), "manca il nome del modello");
        Assertions.assertTrue(body.contains("Struttura"), "manca il pannello della struttura");
        Assertions.assertTrue(body.contains("Schema originale"), "manca lo schema originale");

        Assertions.assertTrue(body.contains("/services/4/models/2"), "manca il collegamento a Party");
    }

    @Test
    @DisplayName("un modello di un altro servizio non è raggiungibile da questo")
    void modelloDiAltroServizio_rifiutato() throws Exception {
        ApiService altro = mock(ApiService.class, RETURNS_DEEP_STUBS);
        when(altro.getId()).thenReturn(999L);
        ApiModel estraneo = mock(ApiModel.class);
        when(estraneo.getId()).thenReturn(50L);
        when(estraneo.getService()).thenReturn(altro);
        when(specVersions.model(50L)).thenReturn(estraneo);

        mvc.perform(get("/services/{sid}/models/{mid}", SERVICE_ID, 50L).param("lang", "it"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("servizio senza specifica importata: elenco vuoto, nessun errore")
    void nessunaRevisione_elencoVuoto() throws Exception {
        when(specVersions.resolveVersion(anyLong(), any())).thenReturn(Optional.empty());

        mvc.perform(get("/services/{id}/models", SERVICE_ID).param("lang", "it"))
                .andExpect(status().isOk())
                .andExpect(view().name("services/models"));
    }
}
