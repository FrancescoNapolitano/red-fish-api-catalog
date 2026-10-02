package it.fn.redfish.catalog.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import it.fn.redfish.catalog.domain.SpecFormat;
import it.fn.redfish.catalog.support.Text;

class Petstore3ImportTest {

    private static String content;

    private final SpecFormatDetector detector = new SpecFormatDetector();
    private final OpenApiSpecParser parser = new OpenApiSpecParser();

    @BeforeAll
    static void leggiFixture() throws Exception {
        try (var in = Petstore3ImportTest.class.getResourceAsStream("/petstore3-minified.json")) {
            content = Text.stripBom(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    @DisplayName("la specifica viene riconosciuta e analizzata senza errori")
    void importCompleto() {
        SpecFormat format = detector.detect("openapi.json", content);
        assertThat(format).isEqualTo(SpecFormat.OPENAPI_3);

        ParsedSpec parsed = parser.parse(content, format);

        assertThat(parsed.getTitle()).contains("Petstore");
        assertThat(parsed.getEndpoints())
                .describedAs("petstore3 dichiara una ventina di operazioni")
                .hasSizeGreaterThan(10);
        assertThat(parsed.getModels())
                .describedAs("Pet, Order, Category, User, Tag, ApiResponse, Address, Customer")
                .hasSizeGreaterThan(5);
    }

    @Test
    @DisplayName("gli endpoint noti compaiono con metodo e path corretti")
    void endpointAttesi() {
        ParsedSpec parsed = parser.parse(content, detector.detect("openapi.json", content));

        assertThat(parsed.getEndpoints())
                .anySatisfy(e -> {
                    assertThat(e.getHttpMethod()).isEqualTo("POST");
                    assertThat(e.getPath()).isEqualTo("/pet");
                })
                .anySatisfy(e -> {
                    assertThat(e.getHttpMethod()).isEqualTo("GET");
                    assertThat(e.getPath()).isEqualTo("/pet/findByStatus");
                });
    }

    @Test
    @DisplayName("i modelli portano con sé lo schema, non solo il nome")
    void modelliConSchema() {
        ParsedSpec parsed = parser.parse(content, detector.detect("openapi.json", content));

        assertThat(parsed.getModels())
                .anySatisfy(m -> {
                    assertThat(m.name()).isEqualTo("Pet");
                    assertThat(m.schemaJson()).isNotBlank();
                    assertThat(m.schemaJson()).contains("photoUrls");
                });
    }
}
