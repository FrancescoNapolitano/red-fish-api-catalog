package it.fn.redfish.catalog.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

class SchemaRendererTest {
    @org.junit.jupiter.api.BeforeEach
    void useItalianMessages() {
        org.springframework.context.i18n.LocaleContextHolder.setLocale(java.util.Locale.ITALIAN);
    }

    @org.junit.jupiter.api.AfterEach
    void resetLocale() {
        org.springframework.context.i18n.LocaleContextHolder.resetLocaleContext();
    }


    private final SchemaRenderer renderer = new SchemaRenderer();

    private Map<String, JsonNode> models(String... pairs) {
        Map<String, JsonNode> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], renderer.read(pairs[i + 1]));
        }
        return map;
    }

    @Test
    void espandeLeProprietaDiUnOggetto() {
        SchemaView view = renderer.toView("""
                {
                  "type": "object",
                  "required": ["id"],
                  "properties": {
                    "id": { "type": "string", "format": "uuid", "description": "Identificativo" },
                    "amount": { "type": "number", "minimum": 0.01 }
                  }
                }
                """, Map.of());

        assertThat(view.getType()).isEqualTo("object");
        assertThat(view.getChildren()).hasSize(2);

        SchemaView id = view.getChildren().get(0);
        assertThat(id.getName()).isEqualTo("id");
        assertThat(id.isRequired()).isTrue();
        assertThat(id.getTypeLabel()).isEqualTo("string (uuid)");
        assertThat(id.getDescription()).isEqualTo("Identificativo");

        SchemaView amount = view.getChildren().get(1);
        assertThat(amount.isRequired()).isFalse();
        assertThat(amount.getConstraints()).contains("minimum: 0.01");
    }

    @Test
    void risolveIRiferimentiSuiModelli() {
        Map<String, JsonNode> models = models("Party", """
                { "type": "object", "properties": { "iban": { "type": "string" } } }
                """);

        SchemaView view = renderer.toView("""
                { "type": "object", "properties": { "creditor": { "$ref": "#/components/schemas/Party" } } }
                """, models);

        SchemaView creditor = view.getChildren().get(0);
        assertThat(creditor.getRefName()).isEqualTo("Party");
        assertThat(creditor.getChildren()).extracting(SchemaView::getName).containsExactly("iban");
    }

    @Test
    void interrompeLaRicorsioneSuiRiferimentiCiclici() {
        Map<String, JsonNode> models = models("Node", """
                { "type": "object", "properties": { "child": { "$ref": "#/components/schemas/Node" } } }
                """);

        SchemaView view = renderer.toView("{ \"$ref\": \"#/components/schemas/Node\" }", models);

        assertThat(view.getRefName()).isEqualTo("Node");
        SchemaView child = view.getChildren().get(0);
        assertThat(child.isTruncated()).isTrue();
    }

    @Test
    void segnalaIRiferimentiNonRisolvibili() {
        SchemaView view = renderer.toView("{ \"$ref\": \"#/components/schemas/Sconosciuto\" }", Map.of());

        assertThat(view.getRefName()).isEqualTo("Sconosciuto");
        assertThat(view.isTruncated()).isTrue();
    }

    @Test
    void appiattisceGliArrayMostrandoIlTipoDegliElementi() {
        Map<String, JsonNode> models = models("Payment", """
                { "type": "object", "properties": { "id": { "type": "string" } } }
                """);

        SchemaView view = renderer.toView("""
                { "type": "array", "items": { "$ref": "#/components/schemas/Payment" } }
                """, models);

        assertThat(view.getTypeLabel()).isEqualTo("array<Payment>");
        assertThat(view.getChildren()).extracting(SchemaView::getName).containsExactly("id");
    }

    @Test
    void unisceLeProprietaDiAllOf() {
        Map<String, JsonNode> models = models(
                "Base", "{ \"type\": \"object\", \"properties\": { \"id\": { \"type\": \"string\" } } }",
                "Extra", "{ \"type\": \"object\", \"properties\": { \"note\": { \"type\": \"string\" } } }");

        SchemaView view = renderer.toView("""
                { "allOf": [ { "$ref": "#/components/schemas/Base" }, { "$ref": "#/components/schemas/Extra" } ] }
                """, models);

        assertThat(view.getChildren()).extracting(SchemaView::getName).containsExactly("id", "note");
    }

    @Test
    void mostraOneOfComeAlternative() {
        SchemaView view = renderer.toView("""
                { "oneOf": [ { "type": "string" }, { "type": "integer" } ] }
                """, Map.of());

        assertThat(view.getChildren()).extracting(SchemaView::getName)
                .containsExactly("opzione 1", "opzione 2");
    }

    @Test
    void raccoglieIValoriEnum() {
        SchemaView view = renderer.toView("{ \"type\": \"string\", \"enum\": [\"EUR\", \"USD\"] }", Map.of());

        assertThat(view.getEnumValues()).containsExactly("EUR", "USD");
    }

    @Test
    void generaUnEsempioCoerenteConLoSchema() {
        Map<String, JsonNode> models = models("Party",
                "{ \"type\": \"object\", \"properties\": { \"iban\": { \"type\": \"string\" } } }");

        String sample = renderer.sampleJson(renderer.read("""
                {
                  "type": "object",
                  "properties": {
                    "id": { "type": "string", "format": "uuid" },
                    "createdAt": { "type": "string", "format": "date-time" },
                    "amount": { "type": "number" },
                    "count": { "type": "integer" },
                    "active": { "type": "boolean" },
                    "status": { "type": "string", "enum": ["PENDING", "DONE"] },
                    "creditor": { "$ref": "#/components/schemas/Party" },
                    "tags": { "type": "array", "items": { "type": "string" } }
                  }
                }
                """), models);

        assertThat(sample)
                .contains("3fa85f64-5717-4562-b3fc-2c963f66afa6")
                .contains("2026-01-31T10:15:30Z")
                .contains("\"amount\" : 1.5")
                .contains("\"count\" : 1")
                .contains("\"active\" : true")
                .contains("\"status\" : \"PENDING\"")
                .contains("\"iban\"")
                .contains("\"tags\" : [ \"string\" ]");
    }

    @Test
    void preferisceGliEsempiDichiarati() {
        String sample = renderer.sampleJson(renderer.read("""
                { "type": "object", "example": { "id": "esempio-fornito" } }
                """), Map.of());

        assertThat(sample).contains("esempio-fornito");
    }

    @Test
    void risolveIRiferimentiAiTipiProtoQualificati() {
        Map<String, JsonNode> index = models("Outer.Inner",
                "{ \"type\": \"object\", \"properties\": { \"value\": { \"type\": \"string\" } } }");
        SchemaView view = renderer.toView("{ \"$ref\": \"#/components/schemas/Outer.Inner\" }", index);
        assertThat(view.getChildren()).extracting(SchemaView::getName).containsExactly("value");
    }

    @Test
    void formattaJsonCompatto() {
        assertThat(renderer.prettyPrint("{\"a\":1}")).contains("\"a\" : 1");
    }

    @Test
    void gestisceJsonNonValidoSenzaFallire() {
        assertThat(renderer.read("non json")).isNull();
        assertThat(renderer.toView((String) null, Map.of()).getType()).isEqualTo("—");
    }
}
