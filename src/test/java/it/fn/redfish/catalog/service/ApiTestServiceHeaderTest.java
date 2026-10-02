package it.fn.redfish.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiTestServiceHeaderTest {

    @Test
    void interpretaLeRigheNomeValore() {
        var headers = ApiTestService.parseHeaderLines("""
                Content-Type: application/json
                Authorization: Bearer abc.def
                """);

        assertThat(headers)
                .containsEntry("Content-Type", "application/json")
                .containsEntry("Authorization", "Bearer abc.def");
    }

    @Test
    void ignoraRigheVuoteECommenti() {
        var headers = ApiTestService.parseHeaderLines("""
                # commento

                X-Tenant: acme
                """);

        assertThat(headers).hasSize(1).containsEntry("X-Tenant", "acme");
    }

    @Test
    void ignoraRigheSenzaDuePunti() {
        assertThat(ApiTestService.parseHeaderLines("solo-testo")).isEmpty();
    }

    @Test
    void conservaIDuePuntiNelValore() {
        var headers = ApiTestService.parseHeaderLines("Location: https://example.com:8080/x");

        assertThat(headers).containsEntry("Location", "https://example.com:8080/x");
    }

    @Test
    void gestisceInputNullOVuoto() {
        assertThat(ApiTestService.parseHeaderLines(null)).isEmpty();
        assertThat(ApiTestService.parseHeaderLines("   ")).isEmpty();
    }
}
