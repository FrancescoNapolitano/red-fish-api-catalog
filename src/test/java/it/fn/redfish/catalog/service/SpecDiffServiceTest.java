package it.fn.redfish.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import it.fn.redfish.catalog.domain.ApiEndpoint;
import it.fn.redfish.catalog.domain.ApiGroup;
import it.fn.redfish.catalog.domain.ApiModel;
import it.fn.redfish.catalog.domain.ApiService;
import it.fn.redfish.catalog.domain.ModelKind;
import it.fn.redfish.catalog.domain.ServiceType;
import it.fn.redfish.catalog.domain.SourceType;
import it.fn.redfish.catalog.domain.SpecFormat;
import it.fn.redfish.catalog.domain.SpecVersion;
import it.fn.redfish.catalog.repo.ApiEndpointRepository;
import it.fn.redfish.catalog.repo.ApiModelRepository;
import it.fn.redfish.catalog.repo.SpecVersionRepository;
import it.fn.redfish.catalog.spec.SchemaRenderer;
import it.fn.redfish.catalog.support.BusinessException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SpecDiffServiceTest {
    @org.junit.jupiter.api.BeforeEach
    void useItalianMessages() {
        org.springframework.context.i18n.LocaleContextHolder.setLocale(java.util.Locale.ITALIAN);
    }

    @org.junit.jupiter.api.AfterEach
    void resetLocale() {
        org.springframework.context.i18n.LocaleContextHolder.resetLocaleContext();
    }


    private static final long SERVICE_ID = 7L;
    private static final long V1 = 100L;
    private static final long V2 = 200L;

    @Mock
    private SpecVersionRepository specVersions;
    @Mock
    private ApiEndpointRepository endpoints;
    @Mock
    private ApiModelRepository models;

    private SpecDiffService service;
    private ApiService apiService;
    private SpecVersion version1;
    private SpecVersion version2;

    @BeforeEach
    void setUp() {
        service = new SpecDiffService(specVersions, endpoints, models, new SchemaRenderer());

        ApiGroup group = new ApiGroup("Finance", "finance", null);
        group.setPath("/finance");
        apiService = new ApiService(group, "Payments", "payments", ServiceType.REST);
        setId(apiService, SERVICE_ID);

        version1 = specVersion(1, V1);
        version2 = specVersion(2, V2);

        when(specVersions.findById(V1)).thenReturn(Optional.of(version1));
        when(specVersions.findById(V2)).thenReturn(Optional.of(version2));
        when(models.findBySpecVersionIdOrderBySortOrderAscNameAsc(anyLong())).thenReturn(List.of());
    }

    private SpecVersion specVersion(int revision, long id) {
        SpecVersion version = new SpecVersion(apiService, revision, SpecFormat.OPENAPI_3, SourceType.UPLOAD);
        version.setRawContent("{}");
        setId(version, id);
        return version;
    }

    private ApiEndpoint endpoint(SpecVersion version, String method, String path) {
        return new ApiEndpoint(version, method, path);
    }

    private ApiModel model(SpecVersion version, String name, String schemaJson) {
        ApiModel model = new ApiModel(version, name, ModelKind.SCHEMA);
        model.setSchemaJson(schemaJson);
        return model;
    }

    private static void setId(Object entity, long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private void givenEndpoints(List<ApiEndpoint> before, List<ApiEndpoint> after) {
        when(endpoints.findBySpecVersionIdOrderBySortOrderAsc(V1)).thenReturn(new ArrayList<>(before));
        when(endpoints.findBySpecVersionIdOrderBySortOrderAsc(V2)).thenReturn(new ArrayList<>(after));
    }

    private void givenModels(List<ApiModel> before, List<ApiModel> after) {
        when(models.findBySpecVersionIdOrderBySortOrderAscNameAsc(V1)).thenReturn(new ArrayList<>(before));
        when(models.findBySpecVersionIdOrderBySortOrderAscNameAsc(V2)).thenReturn(new ArrayList<>(after));
    }

    @Test
    void nessunaDifferenzaTraRevisioniIdentiche() {
        givenEndpoints(List.of(endpoint(version1, "GET", "/a")), List.of(endpoint(version2, "GET", "/a")));

        var diff = service.diff(SERVICE_ID, V1, V2);

        assertThat(diff.isEmpty()).isTrue();
        assertThat(diff.totalChanges()).isZero();
    }

    @Test
    void rilevaEndpointAggiuntiERimossi() {
        givenEndpoints(
                List.of(endpoint(version1, "GET", "/a"), endpoint(version1, "DELETE", "/a/{id}")),
                List.of(endpoint(version2, "GET", "/a"), endpoint(version2, "POST", "/b")));

        var diff = service.diff(SERVICE_ID, V1, V2);

        assertThat(diff.endpointsAdded()).extracting(SpecDiffService.EndpointChange::path).containsExactly("/b");
        assertThat(diff.endpointsRemoved()).extracting(SpecDiffService.EndpointChange::path).containsExactly("/a/{id}");
        assertThat(diff.endpointsChanged()).isEmpty();
    }

    @Test
    void rilevaLaDeprecazioneDiUnEndpoint() {
        ApiEndpoint before = endpoint(version1, "GET", "/a");
        ApiEndpoint after = endpoint(version2, "GET", "/a");
        after.setDeprecated(true);
        givenEndpoints(List.of(before), List.of(after));

        var diff = service.diff(SERVICE_ID, V1, V2);

        assertThat(diff.endpointsChanged()).singleElement()
                .satisfies(c -> assertThat(c.changes()).contains("Marcato come deprecated"));
    }

    @Test
    void rilevaParametriAggiuntiRimossiEModificati() {
        ApiEndpoint before = endpoint(version1, "GET", "/a");
        before.setParametersJson("""
                [ { "name": "page", "in": "query", "required": false, "schema": { "type": "integer" } },
                  { "name": "legacy", "in": "query", "schema": { "type": "string" } } ]
                """);
        ApiEndpoint after = endpoint(version2, "GET", "/a");
        after.setParametersJson("""
                [ { "name": "page", "in": "query", "required": true, "schema": { "type": "string" } },
                  { "name": "size", "in": "query", "schema": { "type": "integer" } } ]
                """);
        givenEndpoints(List.of(before), List.of(after));

        List<String> changes = service.diff(SERVICE_ID, V1, V2).endpointsChanged().get(0).changes();

        assertThat(changes).contains("Parametro aggiunto: size (query)");
        assertThat(changes).contains("Parametro rimosso: legacy (query)");
        assertThat(changes).contains("Parametro page (query): obbligatorietà aggiunta");
        assertThat(changes).contains("Parametro page (query): tipo/schema modificato");
    }

    @Test
    void rilevaLAggiuntaEIlCambioDelRequestBody() {
        ApiEndpoint before = endpoint(version1, "POST", "/a");
        ApiEndpoint after = endpoint(version2, "POST", "/a");
        after.setRequestBodyJson("{ \"required\": true, \"content\": { \"application/json\": { \"schema\": {} } } }");
        givenEndpoints(List.of(before), List.of(after));

        assertThat(service.diff(SERVICE_ID, V1, V2).endpointsChanged().get(0).changes())
                .contains("Request body aggiunto");
    }

    @Test
    void rilevaResponseAggiunteERimosseESchemiCambiati() {
        ApiEndpoint before = endpoint(version1, "GET", "/a");
        before.setResponsesJson("""
                { "200": { "description": "ok", "content": { "application/json": { "schema": { "type": "string" } } } },
                  "500": { "description": "errore" } }
                """);
        ApiEndpoint after = endpoint(version2, "GET", "/a");
        after.setResponsesJson("""
                { "200": { "description": "ok", "content": { "application/json": { "schema": { "type": "object" } } } },
                  "404": { "description": "non trovato" } }
                """);
        givenEndpoints(List.of(before), List.of(after));

        List<String> changes = service.diff(SERVICE_ID, V1, V2).endpointsChanged().get(0).changes();

        assertThat(changes).contains("Response aggiunta: 404");
        assertThat(changes).contains("Response rimossa: 500");
        assertThat(changes).contains("Response 200: schema modificato");
    }

    @Test
    void rilevaLeModificheAiModelli() {
        givenEndpoints(List.of(), List.of());
        givenModels(
                List.of(model(version1, "Payment", """
                        { "type": "object", "required": ["id"],
                          "properties": { "id": { "type": "string" }, "legacy": { "type": "string" } } }
                        """),
                        model(version1, "Vecchio", "{ \"type\": \"object\" }")),
                List.of(model(version2, "Payment", """
                        { "type": "object", "required": ["id", "amount"],
                          "properties": { "id": { "type": "integer" }, "amount": { "type": "number" } } }
                        """),
                        model(version2, "Nuovo", "{ \"type\": \"object\" }")));

        var diff = service.diff(SERVICE_ID, V1, V2);

        assertThat(diff.modelsAdded()).extracting(SpecDiffService.ModelChange::name).containsExactly("Nuovo");
        assertThat(diff.modelsRemoved()).extracting(SpecDiffService.ModelChange::name).containsExactly("Vecchio");
        assertThat(diff.modelsChanged()).singleElement().satisfies(m -> {
            assertThat(m.name()).isEqualTo("Payment");
            assertThat(m.changes()).contains("Campo aggiunto: amount");
            assertThat(m.changes()).contains("Campo rimosso: legacy");
            assertThat(m.changes()).contains("Campo modificato: id");
            assertThat(m.changes()).contains("Campo diventato obbligatorio: amount");
        });
    }

    @Test
    void rilevaLeVariazioniDeiValoriEnum() {
        givenEndpoints(List.of(), List.of());
        givenModels(
                List.of(model(version1, "Stato", "{ \"type\": \"string\", \"enum\": [\"A\", \"B\"] }")),
                List.of(model(version2, "Stato", "{ \"type\": \"string\", \"enum\": [\"A\", \"C\"] }")));

        assertThat(service.diff(SERVICE_ID, V1, V2).modelsChanged().get(0).changes())
                .contains("Valore enum aggiunto: C", "Valore enum rimosso: B");
    }

    @Test
    void rifiutaIlConfrontoDiUnaRevisioneConSeStessa() {
        assertThatThrownBy(() -> service.diff(SERVICE_ID, V1, V1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("due revisioni diverse");
    }

    @Test
    void rifiutaRevisioniDiUnAltroServizio() {
        assertThatThrownBy(() -> service.diff(999L, V1, V2))
                .isInstanceOf(it.fn.redfish.catalog.support.NotFoundException.class);
    }
    @Test
    void distingueParametriObbligatoriDaQuelliOpzionali() {
        ApiEndpoint before = endpoint(version1, "GET", "/a");
        ApiEndpoint after = endpoint(version2, "GET", "/a");
        after.setParametersJson("""
                [{"name":"tenant","in":"query","required":true,"schema":{"type":"string"}},
                 {"name":"page","in":"query","required":false,"schema":{"type":"integer"}}]
                """);
        givenEndpoints(List.of(before), List.of(after));
        assertThat(service.diff(SERVICE_ID, V1, V2).compatibilityRisks())
                .extracting(SpecDiffService.CompatibilityRisk::reason)
                .containsExactly("Parametro obbligatorio aggiunto: tenant (query)");
    }

    @Test
    void segnalaRischiNeiCampiAnnidatiMaNonNelleDescrizioni() {
        givenEndpoints(List.of(), List.of());
        givenModels(List.of(model(version1, "Payment", """
                {"type":"object","properties":{"party":{"type":"object","properties":{"id":{"type":"string"}}}}}
                """)), List.of(model(version2, "Payment", """
                {"type":"object","description":"nuova descrizione","properties":{"party":{"type":"object","properties":{"id":{"type":"integer"}}}}}
                """)));
        assertThat(service.diff(SERVICE_ID, V1, V2).compatibilityRisks()).singleElement()
                .satisfies(risk -> {
                    assertThat(risk.target()).isEqualTo("Payment.party.id");
                    assertThat(risk.reason()).isEqualTo("Definizione modificata: type");
                });
    }

    @Test
    void segnalaEndpointRimosso() {
        givenEndpoints(List.of(endpoint(version1, "GET", "/a")), List.of());
        assertThat(service.diff(SERVICE_ID, V1, V2).compatibilityRisks())
                .extracting(SpecDiffService.CompatibilityRisk::reason).containsExactly("Endpoint rimosso");
    }
}
