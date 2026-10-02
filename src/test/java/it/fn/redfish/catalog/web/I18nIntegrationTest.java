package it.fn.redfish.catalog.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import it.fn.redfish.catalog.service.*;
import it.fn.redfish.catalog.security.PermissionEvaluator;

@WebMvcTest({HomeController.class, SetupController.class, SearchController.class})
@WithMockUser
class I18nIntegrationTest {
    @Autowired MockMvc mvc;
    @MockitoBean SettingsService settings;
    @MockitoBean SetupService setup;
    @MockitoBean DashboardService dashboard;
    @MockitoBean FavoriteService favorites;
    @MockitoBean GroupService groups;
    @MockitoBean SearchService search;
    @MockitoBean(name = "perm") PermissionEvaluator perm;

    @BeforeEach
    void prepare() {
        when(settings.appName()).thenReturn("Catalogo aziendale");
        when(settings.logoPath()).thenReturn("");
        when(perm.currentUser()).thenReturn(Optional.empty());
        when(dashboard.counters()).thenReturn(new DashboardService.Counters(0, 0, 0, 0, 0, 0));
    }

    @Test
    void englishIsDefaultEvenWithAnItalianBrowser() throws Exception {
        when(settings.isSetupCompleted()).thenReturn(true);
        String html = mvc.perform(get("/").header("Accept-Language", "it-IT,it;q=0.9"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("lang=\"en\"", "Global search", "Services without a contact", "Catalogo aziendale")
                .doesNotContain("Ricerca globale", "??ui.");
    }

    @Test
    void languageChoiceIsStoredAndCanBeSwitchedBack() throws Exception {
        when(settings.isSetupCompleted()).thenReturn(true);
        var response = mvc.perform(get("/login").param("lang", "it"))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).contains("Accedi al catalogo", "lang=\"it\"");
        Cookie cookie = response.getCookie("catalog-language");
        assertThat(cookie).isNotNull();
        assertThat(cookie.getMaxAge()).isGreaterThan(0);
        String remembered = mvc.perform(get("/login").cookie(cookie)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(remembered).contains("Accedi al catalogo");
        var english = mvc.perform(get("/login").cookie(cookie).param("lang", "en"))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(english.getContentAsString(StandardCharsets.UTF_8)).contains("Sign in to the catalog");
        assertThat(english.getCookie("catalog-language").getValue()).startsWith("en");
    }

    @Test
    void unsupportedLanguagesFallBackToEnglish() throws Exception {
        when(settings.isSetupCompleted()).thenReturn(true);
        String html = mvc.perform(get("/login").param("lang", "fr").cookie(new Cookie("catalog-language", "de")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("lang=\"en\"", "Sign in to the catalog");
        html = mvc.perform(get("/login").cookie(new Cookie("catalog-language", "de")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("lang=\"en\"");
    }

    @Test
    void validationMessagesFollowTheSelectedLanguage() throws Exception {
        String english = mvc.perform(post("/setup/step1").with(csrf()).param("appName", ""))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(english).contains("The application name is required", "The password is required")
                .doesNotContain("{message.", "??");
        String italian = mvc.perform(post("/setup/step1").with(csrf()).param("appName", "").param("lang", "it"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(italian).contains("Il nome dell'applicazione è obbligatorio", "La password è obbligatoria")
                .doesNotContain("{message.", "??");
    }

    @Test
    void switchingLanguageKeepsTheSetupFormAndSummary() throws Exception {
        var session = new MockHttpSession();
        mvc.perform(post("/setup/step1").session(session).with(csrf())
                .param("appName", "My catalog").param("adminUsername", "admin")
                .param("adminFirstName", "Mario").param("adminLastName", "Rossi")
                .param("adminEmail", "mario@example.com").param("adminPassword", "test-password")
                .param("adminPasswordConfirm", "test-password"))
                .andExpect(status().isOk());
        String html = mvc.perform(get("/setup").session(session).param("step", "2").param("lang", "it"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("My catalog", "Mario Rossi", "Completa configurazione", "step=2")
                .doesNotContain("test-password");
    }

    @Test
    void languageLinksPreserveSearchFiltersAndPagination() throws Exception {
        when(settings.isSetupCompleted()).thenReturn(true);
        when(search.search("pay", null, null, null, 2)).thenReturn(
                new SearchService.SearchResults("pay", List.of(), List.of(), List.of(), List.of(), List.of(), 2, true));
        String html = mvc.perform(get("/search?q=pay&page=2"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("q=pay&amp;page=2&amp;lang=it", "Page 3").doesNotContain("??");
    }
}
