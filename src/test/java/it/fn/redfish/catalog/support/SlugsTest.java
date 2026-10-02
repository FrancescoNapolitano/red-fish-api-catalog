package it.fn.redfish.catalog.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SlugsTest {

    @Test
    void normalizzaSpaziEMaiuscole() {
        assertThat(Slugs.of("Payments SEPA")).isEqualTo("payments-sepa");
    }

    @Test
    void rimuoveAccentiEPunteggiatura() {
        assertThat(Slugs.of("Notifiche à/è (Email)")).isEqualTo("notifiche-a-e-email");
    }

    @Test
    void rimuoveTrattiniAiBordi() {
        assertThat(Slugs.of("--- ciao ---")).isEqualTo("ciao");
    }

    @Test
    void restituisceStringaVuotaSenzaCaratteriValidi() {
        assertThat(Slugs.of("///")).isEmpty();
        assertThat(Slugs.of(null)).isEmpty();
    }

    @Test
    void usaIlFallbackQuandoNonRestaNulla() {
        assertThat(Slugs.orFallback("###", "gruppo")).isEqualTo("gruppo");
        assertThat(Slugs.orFallback("Finance", "gruppo")).isEqualTo("finance");
    }

    @Test
    void troncaSlugMoltoLunghi() {
        assertThat(Slugs.of("a".repeat(300))).hasSize(160);
    }
}
