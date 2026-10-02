package it.fn.redfish.catalog.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import it.fn.redfish.catalog.security.PermissionEvaluator;
import it.fn.redfish.catalog.service.ApiTestService;
import it.fn.redfish.catalog.service.GroupService;
import it.fn.redfish.catalog.service.ServiceCatalogService;
import it.fn.redfish.catalog.service.SettingsService;
import it.fn.redfish.catalog.service.SpecVersionService;

@WebMvcTest(ApiTestController.class)
@WithMockUser
class ApiTestConsoleTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ApiTestRunner runner;
    @MockitoBean
    private ServiceCatalogService catalog;
    @MockitoBean
    private SpecVersionService specVersions;
    @MockitoBean
    private SettingsService settings;
    @MockitoBean
    private GroupService groupService;
    @MockitoBean(name = "perm")
    private PermissionEvaluator perm;

    @BeforeEach
    void stub() {
        when(settings.isSetupCompleted()).thenReturn(true);
        when(settings.appName()).thenReturn("API Catalog");
        when(settings.logoPath()).thenReturn("");
        when(settings.apiTestTimeout()).thenReturn(Duration.ofSeconds(30));
        when(perm.currentUser()).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("console vuota: nessuna chiamata eseguita")
    void console_senzaEsito() throws Exception {
        mvc.perform(get("/test").param("lang", "it"))
                .andExpect(status().isOk())
                .andExpect(view().name("test/console"))
                .andExpect(content().string(Matchers.containsString("Nessuna chiamata eseguita")));
    }

    @Test
    @DisplayName("esecuzione: la pagina torna con l'esito e il modulo ripopolato")
    void esecuzione_rendeEsitoEConservaIDati() throws Exception {
        ApiTestService.TestResponse response = new ApiTestService.TestResponse(
                200, "OK", 42L, "{\"stato\":\"UP\"}", "application/json",
                List.of(Map.entry("Content-Type", "application/json")), 15L, false, null,
                "https://api.example.com/health");
        when(runner.run(any())).thenReturn(
                new ApiTestRunner.Outcome(response, "curl https://api.example.com/health", null));

        String body = mvc.perform(post("/test/execute").with(csrf())
                        .param("method", "GET")
                        .param("url", "https://api.example.com/health")
                        .param("headers", "Accept: application/json")
                        .param("body", "")
                        .param("queryParams", "").param("lang", "it"))
                .andExpect(status().isOk())
                .andExpect(view().name("test/console"))
                .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertTrue(body.contains("200 OK"), "manca l'esito");
        org.junit.jupiter.api.Assertions.assertTrue(body.contains("42 ms"), "manca la durata");

        org.junit.jupiter.api.Assertions.assertTrue(
                body.contains("Accept: application/json"), "headers non ripopolati");
        org.junit.jupiter.api.Assertions.assertTrue(
                body.contains("https://api.example.com/health"), "url non ripopolato");
        org.junit.jupiter.api.Assertions.assertFalse(
                body.contains("Nessuna chiamata eseguita"), "mostra ancora lo stato iniziale");
    }

    @Test
    @DisplayName("chiamata fallita: il messaggio d'errore è visibile")
    void esecuzione_fallita_mostraErrore() throws Exception {
        ApiTestService.TestResponse response = new ApiTestService.TestResponse(
                0, null, 5L, null, null, List.of(), 0L, false, "Connessione rifiutata", null);
        when(runner.run(any())).thenReturn(new ApiTestRunner.Outcome(response, "curl ...", null));

        mvc.perform(post("/test/execute").with(csrf())
                        .param("method", "GET")
                        .param("url", "https://spento.example.com").param("lang", "it"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Connessione rifiutata")));
    }
}
