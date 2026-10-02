package it.fn.redfish.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import it.fn.redfish.catalog.domain.ApiEndpoint;
import it.fn.redfish.catalog.domain.ApiGroup;
import it.fn.redfish.catalog.domain.ApiService;
import it.fn.redfish.catalog.domain.ServiceType;
import it.fn.redfish.catalog.domain.SourceType;
import it.fn.redfish.catalog.domain.SpecFormat;
import it.fn.redfish.catalog.domain.SpecVersion;
import it.fn.redfish.catalog.spec.EndpointView;

class CurlBuilderTest {

    private final CurlBuilder builder = new CurlBuilder();

    private ApiEndpoint endpoint(String method, String path) {
        ApiGroup group = new ApiGroup("Finance", "finance", null);
        group.setPath("/finance");
        ApiService service = new ApiService(group, "Payments", "payments", ServiceType.REST);
        SpecVersion version = new SpecVersion(service, 1, SpecFormat.OPENAPI_3, SourceType.UPLOAD);
        return new ApiEndpoint(version, method, path);
    }

    private EndpointView.ParamView param(String name, String in, boolean required, String example) {
        return new EndpointView.ParamView(name, in, required, false, null, "string", null, example, List.of(), null);
    }

    @Test
    void costruisceUnGetSemplice() {
        EndpointView view = new EndpointView(endpoint("GET", "/payments"));

        String curl = builder.fromEndpoint(view, "https://api.example.com");

        assertThat(curl).startsWith("curl -i -X GET");
        assertThat(curl).contains("'https://api.example.com/payments'");
    }

    @Test
    void sostituisceIParametriDiPathConGliEsempi() {
        EndpointView view = new EndpointView(endpoint("GET", "/payments/{id}"));
        view.getPathParams().add(param("id", "path", true, "42"));

        assertThat(builder.fromEndpoint(view, "https://api.example.com"))
                .contains("https://api.example.com/payments/42");
    }

    @Test
    void usaUnSegnapostoLeggibileQuandoMancaLEsempio() {
        EndpointView view = new EndpointView(endpoint("GET", "/payments/{id}"));
        view.getPathParams().add(param("id", "path", true, null));

        assertThat(builder.fromEndpoint(view, "https://api.example.com")).contains("/payments/<id>");
    }

    @Test
    void aggiungeSoloIQueryParamObbligatori() {
        EndpointView view = new EndpointView(endpoint("GET", "/payments"));
        view.getQueryParams().add(param("tenant", "query", true, "acme"));
        view.getQueryParams().add(param("page", "query", false, "0"));

        String curl = builder.fromEndpoint(view, "https://api.example.com");

        assertThat(curl).contains("tenant=acme");
        assertThat(curl).doesNotContain("page=");
    }

    @Test
    void aggiungeGliHeaderObbligatoriEIlContentType() {
        EndpointView view = new EndpointView(endpoint("POST", "/payments"));
        view.getHeaderParams().add(param("X-Tenant", "header", true, "acme"));
        view.setRequestBody(new EndpointView.BodyView(true, null,
                List.of(new EndpointView.ContentView("application/json", null, "{\"amount\":1}"))));

        String curl = builder.fromEndpoint(view, "https://api.example.com");

        assertThat(curl).contains("-H 'X-Tenant: acme'");
        assertThat(curl).contains("-H 'Content-Type: application/json'");
        assertThat(curl).contains("-d '{\"amount\":1}'");
    }

    @Test
    void suggerisceLHeaderDiAutenticazioneInBaseAlloSchema() {
        EndpointView view = new EndpointView(endpoint("GET", "/payments"));
        view.getSecurity().add(new EndpointView.SecurityView("bearerAuth", "http", "bearer", null, null, null));

        assertThat(builder.fromEndpoint(view, "https://api.example.com"))
                .contains("-H 'Authorization: Bearer <token>'");
    }

    @Test
    void suggerisceBasicPerLoSchemaBasic() {
        EndpointView view = new EndpointView(endpoint("GET", "/payments"));
        view.getSecurity().add(new EndpointView.SecurityView("basicAuth", "http", "basic", null, null, null));

        assertThat(builder.fromEndpoint(view, "https://api.example.com"))
                .contains("Basic <base64(user:password)>");
    }

    @Test
    void suggerisceLApiKeyInHeader() {
        EndpointView view = new EndpointView(endpoint("GET", "/payments"));
        view.getSecurity().add(new EndpointView.SecurityView("X-Api-Key", "apiKey", null, "header", null, null));

        assertThat(builder.fromEndpoint(view, "https://api.example.com")).contains("-H 'X-Api-Key: <api-key>'");
    }

    @Test
    void preferisceLAmbienteSelezionato() {
        EndpointView view = new EndpointView(endpoint("GET", "/payments"));
        view.getServers().add(new EndpointView.ServerView("https://spec.example.com/v2", null));

        assertThat(builder.fromEndpoint(view, "https://fallback.example.com"))
                .contains("https://fallback.example.com/payments");
    }

    @Test
    void usaIlPercorsoCompletoDellAmbiente() {
        EndpointView view = new EndpointView(endpoint("GET", "/payments"));
        view.getServers().add(new EndpointView.ServerView("/api/v2", null));

        assertThat(builder.fromEndpoint(view, "https://fallback.example.com/"))
                .contains("https://fallback.example.com/payments");
    }

    @Test
    void perGrpcSuggerisceGrpcurl() {
        ApiEndpoint rpc = endpoint("RPC", "/notification.v1.NotificationService/Send");
        rpc.setGrpcService("notification.v1.NotificationService");
        rpc.setGrpcMethod("Send");
        EndpointView view = new EndpointView(rpc);

        String curl = builder.fromEndpoint(view, "https://grpc.example.com:50051");

        assertThat(curl).contains("grpcurl");
        assertThat(curl).contains("grpc.example.com:50051");
        assertThat(curl).contains("notification.v1.NotificationService/Send");
    }

    @Test
    void proteggeLeVirgoletteSingoleNelBody() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");

        String curl = builder.build("POST", "https://api.example.com", "/x", Map.of(), headers,
                "{\"nota\":\"l'importo\"}");

        assertThat(curl).contains("'\\''");
    }

    @Test
    void normalizzaGliSlashDuplicati() {
        String curl = builder.build("GET", "https://api.example.com/", "/x", Map.of(), Map.of(), null);

        assertThat(curl).contains("'https://api.example.com/x'");
    }

    @Test
    void codificaIValoriDeiQueryParam() {
        String curl = builder.build("GET", "https://api.example.com", "/cerca",
                Map.of("q", "mario rossi"), Map.of(), null);

        assertThat(curl).contains("q=mario%20rossi");
    }
    @Test
    void usaLaSpecificaQuandoMancaLAmbiente() {
        EndpointView view = new EndpointView(endpoint("GET", "/payments"));
        view.getServers().add(new EndpointView.ServerView("https://spec.example.com/v2/", null));
        assertThat(builder.fromEndpoint(view, null)).contains("https://spec.example.com/v2/payments");
        assertThat(ApiUrls.endpoint(ApiUrls.resolve(view, null), "/payments"))
                .isEqualTo("https://spec.example.com/v2/payments");
    }

    @Test
    void nonInventaUnHostPerServerRelativiONonConfigurati() {
        EndpointView view = new EndpointView(endpoint("GET", "/payments"));
        view.getServers().add(new EndpointView.ServerView("/api", null));
        assertThat(builder.fromEndpoint(view, null)).isEmpty();
        assertThat(ApiUrls.endpoint(ApiUrls.resolve(view, null), "/payments")).isEmpty();
    }

    @Test
    void conservaLUrlCompletoDellaRichiestaEseguita() {
        assertThat(builder.build("GET", "https://api.example.com/payments?q=test", "", Map.of(), Map.of(), null))
                .contains("'https://api.example.com/payments?q=test'");
    }
}
