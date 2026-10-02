package it.fn.redfish.catalog.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Optional;
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
import it.fn.redfish.catalog.domain.*;

@WebMvcTest({SetupController.class, SearchController.class, HomeController.class})
@WithMockUser
class CatalogImprovementsTest {
    @Autowired MockMvc mvc;
    @MockitoBean SetupService setup;
    @MockitoBean SearchService search;
    @MockitoBean DashboardService dashboard;
    @MockitoBean FavoriteService favorites;
    @MockitoBean SettingsService settings;
    @MockitoBean GroupService groups;
    @MockitoBean(name = "perm") PermissionEvaluator perm;

    @BeforeEach
    void prepare() {
        when(settings.appName()).thenReturn("Catalog");
        when(settings.logoPath()).thenReturn("");
        when(perm.currentUser()).thenReturn(Optional.empty());
    }

    @Test
    void completaIlWizardInDuePassiSenzaUrlGlobale() throws Exception {
        var session = new MockHttpSession();
        String first = mvc.perform(get("/setup").session(session).param("lang", "it")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(first).contains("name=\"appName\"", "name=\"adminUsername\"").doesNotContain("name=\"baseUrl\"");
        mvc.perform(post("/setup/step1").session(session).with(csrf())
                .param("appName", "Catalog").param("adminUsername", "admin")
                .param("adminFirstName", "Mario").param("adminLastName", "Rossi")
                .param("adminEmail", "mario@example.com").param("adminPassword", "test-password")
                .param("adminPasswordConfirm", "test-password").param("lang", "it"))
                .andExpect(status().isOk()).andExpect(model().attribute("step", 2));
        mvc.perform(post("/setup/complete").session(session).with(csrf()).param("lang", "it"))
                .andExpect(redirectedUrl("/login?setup=done&username=admin"));
        verify(setup).complete(any());
    }

    @Test
    void credenzialiIncompleteRestanoNelPrimoPasso() throws Exception {
        mvc.perform(post("/setup/step1").with(csrf()).param("appName", "Catalog").param("lang", "it"))
                .andExpect(status().isOk()).andExpect(model().attribute("step", 1))
                .andExpect(model().attributeHasFieldErrors("setupForm", "adminPassword"));
        verify(setup, never()).complete(any());
    }

    @Test
    void ricercaMantieneFiltriNeiLinkDiPaginazione() throws Exception {
        when(settings.isSetupCompleted()).thenReturn(true);
        var group = new ApiGroup("Finance", "finance", null);
        group.setPath("/finance");
        when(groups.get(7L)).thenReturn(group);
        when(groups.options(null)).thenReturn(List.of(new GroupService.GroupOption(7L, "Finance", "/finance")));
        when(search.search("pay", "/finance", ServiceType.REST, ServiceStatus.ACTIVE, 1))
                .thenReturn(new SearchService.SearchResults("pay", List.of(), List.of(), List.of(), List.of(), List.of(), 1, true));
        String html = mvc.perform(get("/search").param("q", "pay").param("groupId", "7")
                .param("type", "REST").param("status", "ACTIVE").param("page", "1").param("lang", "it"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("groupId=7", "type=REST", "status=ACTIVE", "page=2", "page=0");
    }

    @Test
    void dashboardMostraLeAzioniOperative() throws Exception {
        when(settings.isSetupCompleted()).thenReturn(true);
        when(dashboard.counters()).thenReturn(new DashboardService.Counters(0, 0, 0, 0, 0, 0));
        String html = mvc.perform(get("/").param("lang", "it")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Servizi senza referente", "Ambienti da configurare");
    }
}
