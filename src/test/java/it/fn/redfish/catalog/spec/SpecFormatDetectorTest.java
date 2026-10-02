package it.fn.redfish.catalog.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import it.fn.redfish.catalog.domain.SpecFormat;
import it.fn.redfish.catalog.support.BusinessException;
import it.fn.redfish.catalog.support.Text;

class SpecFormatDetectorTest {
    @org.junit.jupiter.api.BeforeEach
    void useItalianMessages() {
        org.springframework.context.i18n.LocaleContextHolder.setLocale(java.util.Locale.ITALIAN);
    }

    @org.junit.jupiter.api.AfterEach
    void resetLocale() {
        org.springframework.context.i18n.LocaleContextHolder.resetLocaleContext();
    }


    private final SpecFormatDetector detector = new SpecFormatDetector();

    @Test
    void riconosceOpenApi3Yaml() {
        assertThat(detector.detect("api.yaml", "openapi: 3.0.3\ninfo:\n  title: X\n"))
                .isEqualTo(SpecFormat.OPENAPI_3);
    }

    @Test
    void riconosceOpenApi3Json() {
        assertThat(detector.detect("api.json", "{\n  \"openapi\": \"3.1.0\",\n  \"info\": {}\n}"))
                .isEqualTo(SpecFormat.OPENAPI_3);
    }

    @Test
    void riconosceSwagger2() {
        assertThat(detector.detect("swagger.json", "{\n  \"swagger\": \"2.0\",\n  \"info\": {}\n}"))
                .isEqualTo(SpecFormat.OPENAPI_2);
    }

    @Test
    void riconosceProtoDallEstensione() {
        assertThat(detector.detect("service.proto", "service Foo {\n  rpc Bar (A) returns (B);\n}"))
                .isEqualTo(SpecFormat.PROTO);
    }

    @Test
    void riconosceProtoDalContenuto() {
        assertThat(detector.detect("senza-estensione", "syntax = \"proto3\";\npackage a;\n"))
                .isEqualTo(SpecFormat.PROTO);
    }

    @Test
    void protoHaPrecedenzaSuUnEventualeSwaggerNelCommento() {
        String content = "syntax = \"proto3\";\n// swagger: 2.0 citato in un commento\nservice S { rpc M (A) returns (B); }";
        assertThat(detector.detect("s.proto", content)).isEqualTo(SpecFormat.PROTO);
    }

    @Test
    void rifiutaContenutoNonRiconosciuto() {
        assertThatThrownBy(() -> detector.detect("note.txt", "questo è solo testo"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Formato non riconosciuto");
    }

    @Test
    void ilBomNonImpedisceIlRiconoscimento() {
        String withBom = "﻿openapi: 3.0.3\n";
        assertThat(detector.detect("api.yaml", withBom)).isEqualTo(SpecFormat.OPENAPI_3);
        assertThat(detector.detect("api.yaml", Text.stripBom(withBom))).isEqualTo(SpecFormat.OPENAPI_3);
    }

    @Test
    void riconosceOpenApi3JsonMinificato() {
        assertThat(detector.detect("openapi.json", "{\"openapi\":\"3.0.4\",\"info\":{\"title\":\"X\"}}"))
                .isEqualTo(SpecFormat.OPENAPI_3);
    }

    @Test
    void riconosceSwagger2JsonMinificato() {
        assertThat(detector.detect("swagger.json", "{\"swagger\":\"2.0\",\"info\":{\"title\":\"X\"}}"))
                .isEqualTo(SpecFormat.OPENAPI_2);
    }

    @Test
    void riconoscePetstore3ComeServitoDalSito() throws Exception {
        String content;
        try (var in = getClass().getResourceAsStream("/petstore3-minified.json")) {
            content = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }

        assertThat(content.lines().count())
                .describedAs("la fixture deve restare su una riga sola, è il punto del test")
                .isEqualTo(1);
        assertThat(detector.detect("openapi.json", content)).isEqualTo(SpecFormat.OPENAPI_3);
    }
}
