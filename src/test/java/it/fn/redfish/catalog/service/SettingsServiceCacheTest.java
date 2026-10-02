package it.fn.redfish.catalog.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import it.fn.redfish.catalog.config.CatalogProperties;
import it.fn.redfish.catalog.domain.AppSetting;
import it.fn.redfish.catalog.repo.AppSettingRepository;

class SettingsServiceCacheTest {

    private AppSettingRepository repository;
    private SettingsService settings;

    @BeforeEach
    void setUp() {
        repository = mock(AppSettingRepository.class);
        settings = new SettingsService(repository, new CatalogProperties());
        when(repository.findById(anyString())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("i valori vengono letti una volta sola finché nessuno scrive")
    void letturaRipetuta_usaLaCache() {
        when(repository.findAll()).thenReturn(List.of(new AppSetting(SettingsService.APP_NAME, "Catalogo")));

        assertEquals("Catalogo", settings.appName());
        assertEquals("Catalogo", settings.appName());

        verify(repository, times(1)).findAll();
    }

    @Test
    @DisplayName("dopo una scrittura non andata a buon fine la cache non conserva il valore nuovo")
    void scritturaAnnullata_nonRestaInCache() {
        when(repository.findAll()).thenReturn(List.of(new AppSetting(SettingsService.APP_NAME, "Catalogo")));
        assertEquals("Catalogo", settings.appName());

        settings.put(SettingsService.APP_NAME, "Nome mai salvato");

        assertEquals("Catalogo", settings.appName(),
                "la cache ha trattenuto un valore che il database non ha");
        verify(repository, times(2)).findAll();
    }

    @Test
    @DisplayName("dopo una scrittura riuscita si vede il valore nuovo")
    void scritturaRiuscita_valoreAggiornato() {
        when(repository.findAll()).thenReturn(List.of(new AppSetting(SettingsService.APP_NAME, "Catalogo")));
        assertEquals("Catalogo", settings.appName());

        settings.put(SettingsService.APP_NAME, "Nuovo nome");
        when(repository.findAll()).thenReturn(List.of(new AppSetting(SettingsService.APP_NAME, "Nuovo nome")));

        assertEquals("Nuovo nome", settings.appName());
    }
}
