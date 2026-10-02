package it.fn.redfish.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import it.fn.redfish.catalog.domain.ApiModel;
import it.fn.redfish.catalog.repo.ApiEndpointRepository;
import it.fn.redfish.catalog.repo.ApiModelRepository;
import it.fn.redfish.catalog.repo.SpecVersionRepository;
import it.fn.redfish.catalog.spec.EndpointViewFactory;
import it.fn.redfish.catalog.spec.SchemaRenderer;
import it.fn.redfish.catalog.support.NotFoundException;

class SpecVersionServiceModelTest {
    @org.junit.jupiter.api.BeforeEach
    void useItalianMessages() {
        org.springframework.context.i18n.LocaleContextHolder.setLocale(java.util.Locale.ITALIAN);
    }

    @org.junit.jupiter.api.AfterEach
    void resetLocale() {
        org.springframework.context.i18n.LocaleContextHolder.resetLocaleContext();
    }


    private ApiModelRepository models;
    private SpecVersionService service;

    @BeforeEach
    void setUp() {
        models = mock(ApiModelRepository.class);
        service = new SpecVersionService(
                mock(SpecVersionRepository.class),
                mock(ApiEndpointRepository.class),
                models,
                mock(EndpointViewFactory.class),
                new SchemaRenderer(),
                mock(CurlBuilder.class));
    }

    @Test
    @DisplayName("il modello viene caricato con servizio e revisione già risolti")
    void model_caricaLeRelazioni() {
        ApiModel atteso = mock(ApiModel.class);
        when(models.findByIdWithRelations(7L)).thenReturn(Optional.of(atteso));

        assertThat(service.model(7L)).isSameAs(atteso);

        verify(models).findByIdWithRelations(7L);
        verify(models, never()).findById(any());
    }

    @Test
    @DisplayName("un modello inesistente non viene trovato")
    void model_inesistente() {
        when(models.findByIdWithRelations(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.model(404L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Modello");
    }
}
