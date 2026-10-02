package it.fn.redfish.catalog.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class ModelReferencesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode json(String text) {
        try {
            return MAPPER.readTree(text);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("raccoglie i riferimenti diretti")
    void riferimentiDiretti() {
        JsonNode body = json("""
                {"content":{"application/json":{"schema":{"$ref":"#/components/schemas/Transfer"}}}}
                """);

        Set<String> found = ModelReferences.collect(Map.of(), body);

        assertEquals(Set.of("Transfer"), found);
    }

    @Test
    @DisplayName("segue i riferimenti annidati fra un modello e l'altro")
    void riferimentiAnnidati() {
        Map<String, JsonNode> models = Map.of(
                "Transfer", json("""
                        {"properties":{"debtor":{"$ref":"#/components/schemas/Party"},
                                       "status":{"$ref":"#/components/schemas/Status"}}}
                        """),
                "Party", json("""
                        {"properties":{"account":{"$ref":"#/components/schemas/Account"}}}
                        """),
                "Account", json("{\"type\":\"object\"}"),
                "Status", json("{\"enum\":[\"PENDING\",\"DONE\"]}"),
                "NonUsato", json("{\"type\":\"object\"}"));

        Set<String> found = ModelReferences.collect(models,
                json("{\"schema\":{\"$ref\":\"#/components/schemas/Transfer\"}}"));

        assertEquals(Set.of("Transfer", "Party", "Account", "Status"), found);
        assertTrue(found.stream().noneMatch("NonUsato"::equals), "un modello non citato non va incluso");
    }

    @Test
    @DisplayName("raccoglie da più radici: corpo della richiesta e risposte")
    void piuRadici() {
        JsonNode request = json("{\"$ref\":\"#/components/schemas/CreateTransfer\"}");
        JsonNode responses = json("""
                {"200":{"content":{"application/json":{"schema":{"$ref":"#/components/schemas/Transfer"}}}},
                 "400":{"content":{"application/json":{"schema":{"$ref":"#/components/schemas/Errore"}}}}}
                """);

        Set<String> found = ModelReferences.collect(Map.of(), request, responses);

        assertEquals(Set.of("CreateTransfer", "Transfer", "Errore"), found);
    }

    @Test
    @DisplayName("uno schema ciclico non manda in ricorsione infinita")
    void schemaCiclico() {
        Map<String, JsonNode> models = Map.of(
                "Nodo", json("""
                        {"properties":{"figli":{"type":"array",
                                                "items":{"$ref":"#/components/schemas/Nodo"}},
                                       "gemello":{"$ref":"#/components/schemas/Altro"}}}
                        """),
                "Altro", json("{\"properties\":{\"torna\":{\"$ref\":\"#/components/schemas/Nodo\"}}}"));

        Set<String> found = assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
                ModelReferences.collect(models, json("{\"$ref\":\"#/components/schemas/Nodo\"}")));

        assertEquals(Set.of("Nodo", "Altro"), found);
    }

    @Test
    @DisplayName("radici nulle o vuote non danno errore")
    void radiciAssenti() {
        assertTrue(ModelReferences.collect(null, (JsonNode) null).isEmpty());
        assertTrue(ModelReferences.collect(Map.of()).isEmpty());
        assertTrue(ModelReferences.collect(Map.of(), json("{}")).isEmpty());
    }

    @Test
    @DisplayName("l'ordine di incontro viene conservato")
    void ordineConservato() {
        JsonNode responses = json("""
                {"a":{"$ref":"#/components/schemas/Primo"},
                 "b":{"$ref":"#/components/schemas/Secondo"}}
                """);

        assertEquals(List.of("Primo", "Secondo"),
                List.copyOf(ModelReferences.collect(Map.of(), responses)));
    }
}
