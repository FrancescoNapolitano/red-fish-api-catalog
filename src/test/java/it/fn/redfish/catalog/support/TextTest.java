package it.fn.redfish.catalog.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextTest {

    @Test
    void rimuoveIlBomIniziale() {
        assertThat(Text.stripBom("﻿openapi: 3.0.3")).isEqualTo("openapi: 3.0.3");
    }

    @Test
    void lasciaInvariatoIlContenutoSenzaBom() {
        assertThat(Text.stripBom("openapi: 3.0.3")).isEqualTo("openapi: 3.0.3");
    }

    @Test
    void gestisceNullEStringaVuota() {
        assertThat(Text.stripBom(null)).isNull();
        assertThat(Text.stripBom("")).isEmpty();
    }

    @Test
    void rimuoveSoloIlPrimoBom() {
        assertThat(Text.stripBom("﻿﻿x")).isEqualTo("﻿x");
    }

    @Test
    void blankToNullAzzeraSoloIValoriVuoti() {
        assertThat(Text.blankToNull(null)).isNull();
        assertThat(Text.blankToNull("")).isNull();
        assertThat(Text.blankToNull("   \t\n ")).isNull();
        assertThat(Text.blankToNull("  ciao  ")).isEqualTo("ciao");
    }

    @Test
    void truncateRispettaIlLimiteAggiungendoIPuntini() {
        assertThat(Text.truncate(null, 5)).isNull();
        assertThat(Text.truncate("abcde", 5)).isEqualTo("abcde");
        assertThat(Text.truncate("abcdef", 5)).isEqualTo("abcd…");
        assertThat(Text.truncate("abcdef", 5)).hasSize(5);
    }

    @Test
    void firstNonWhitespaceIndividuaIlPrimoCarattereUtile() {
        assertThat(Text.firstNonWhitespace("{\"openapi\":\"3.0.3\"}")).isEqualTo('{');
        assertThat(Text.firstNonWhitespace("\n\r\t  [1,2]")).isEqualTo('[');
        assertThat(Text.firstNonWhitespace("openapi: 3.0.3")).isEqualTo('o');
    }

    @Test
    void firstNonWhitespaceRestituisceZeroSenzaCaratteriUtili() {
        assertThat(Text.firstNonWhitespace(null)).isEqualTo((char) 0);
        assertThat(Text.firstNonWhitespace("")).isEqualTo((char) 0);
        assertThat(Text.firstNonWhitespace("  \t\n  ")).isEqualTo((char) 0);
    }

    @Test
    void firstNonWhitespaceEquivaleAStripLeading() {
        for (String raw : new String[]{"{a}", "  {a}", "\n[a]", "openapi", "   ", "", "\t\t{"}) {
            String stripped = raw.stripLeading();
            boolean atteso = stripped.startsWith("{") || stripped.startsWith("[");
            char first = Text.firstNonWhitespace(raw);
            assertThat(first == '{' || first == '[').as("input '%s'", raw).isEqualTo(atteso);
        }
    }
}
