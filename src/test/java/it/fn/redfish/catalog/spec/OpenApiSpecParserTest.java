package it.fn.redfish.catalog.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import it.fn.redfish.catalog.domain.ModelKind;
import it.fn.redfish.catalog.domain.SpecFormat;
import it.fn.redfish.catalog.support.BusinessException;

class OpenApiSpecParserTest {
    @org.junit.jupiter.api.BeforeEach
    void useItalianMessages() {
        org.springframework.context.i18n.LocaleContextHolder.setLocale(java.util.Locale.ITALIAN);
    }

    @org.junit.jupiter.api.AfterEach
    void resetLocale() {
        org.springframework.context.i18n.LocaleContextHolder.resetLocaleContext();
    }


    private final OpenApiSpecParser parser = new OpenApiSpecParser();

    private static final String OPENAPI_3 = """
            openapi: 3.0.3
            info:
              title: Payments API
              version: 2.1.0
              description: Gestione pagamenti
            servers:
              - url: https://api.example.com/v2
            paths:
              /payments:
                parameters:
                  - name: X-Tenant
                    in: header
                    required: true
                    schema:
                      type: string
                get:
                  operationId: listPayments
                  summary: Elenca i pagamenti
                  tags: [payments, read]
                  parameters:
                    - name: page
                      in: query
                      schema:
                        type: integer
                  responses:
                    '200':
                      description: ok
                      content:
                        application/json:
                          schema:
                            type: array
                            items:
                              $ref: '#/components/schemas/Payment'
                post:
                  operationId: createPayment
                  deprecated: true
                  requestBody:
                    required: true
                    content:
                      application/json:
                        schema:
                          $ref: '#/components/schemas/Payment'
                  responses:
                    '201':
                      description: creato
            components:
              schemas:
                Payment:
                  type: object
                  required: [id]
                  properties:
                    id:
                      type: string
                    amount:
                      type: number
                Currency:
                  type: string
                  enum: [EUR, USD]
            """;

    private static final String SWAGGER_2 = """
            {
              "swagger": "2.0",
              "info": { "title": "Legacy API", "version": "1.0" },
              "host": "legacy.example.com",
              "basePath": "/api",
              "paths": {
                "/items/{id}": {
                  "get": {
                    "operationId": "getItem",
                    "summary": "Dettaglio",
                    "produces": ["application/json"],
                    "parameters": [
                      { "name": "id", "in": "path", "required": true, "type": "string" }
                    ],
                    "responses": {
                      "200": {
                        "description": "ok",
                        "schema": { "$ref": "#/definitions/Item" }
                      }
                    }
                  }
                }
              },
              "definitions": {
                "Item": {
                  "type": "object",
                  "properties": { "id": { "type": "string" } }
                }
              }
            }
            """;

    @Test
    void estraeMetadataDaOpenApi3() {
        ParsedSpec parsed = parser.parse(OPENAPI_3, SpecFormat.OPENAPI_3);

        assertThat(parsed.getTitle()).isEqualTo("Payments API");
        assertThat(parsed.getVersion()).isEqualTo("2.1.0");
        assertThat(parsed.getDescription()).isEqualTo("Gestione pagamenti");
        assertThat(parsed.getServersJson()).contains("https://api.example.com/v2");
    }

    @Test
    void estraeTutteLeOperazioni() {
        ParsedSpec parsed = parser.parse(OPENAPI_3, SpecFormat.OPENAPI_3);

        assertThat(parsed.getEndpoints()).hasSize(2);
        assertThat(parsed.getEndpoints()).extracting(ParsedEndpoint::getHttpMethod)
                .containsExactlyInAnyOrder("GET", "POST");
        assertThat(parsed.getEndpoints()).allSatisfy(e -> assertThat(e.getPath()).isEqualTo("/payments"));
    }

    @Test
    void unisceIParametriDelPathItemAQuelliDellOperazione() {
        ParsedEndpoint get = parser.parse(OPENAPI_3, SpecFormat.OPENAPI_3).getEndpoints().stream()
                .filter(e -> e.getHttpMethod().equals("GET")).findFirst().orElseThrow();

        assertThat(get.getParametersJson()).contains("X-Tenant").contains("page");
        assertThat(get.getSummary()).isEqualTo("Elenca i pagamenti");
        assertThat(get.getTags()).isEqualTo("payments, read");
    }

    @Test
    void rilevaOperazioniDeprecateEIlRequestBody() {
        ParsedEndpoint post = parser.parse(OPENAPI_3, SpecFormat.OPENAPI_3).getEndpoints().stream()
                .filter(e -> e.getHttpMethod().equals("POST")).findFirst().orElseThrow();

        assertThat(post.isDeprecated()).isTrue();
        assertThat(post.getRequestBodyJson()).contains("Payment");
        assertThat(post.getConsumes()).isEqualTo("application/json");
    }

    @Test
    void estraeSchemiDistinguendoGliEnum() {
        ParsedSpec parsed = parser.parse(OPENAPI_3, SpecFormat.OPENAPI_3);

        assertThat(parsed.getModels()).hasSize(2);
        assertThat(parsed.getModels()).extracting(ParsedSpec.ParsedModel::name)
                .containsExactlyInAnyOrder("Payment", "Currency");
        assertThat(parsed.getModels()).filteredOn(m -> m.name().equals("Currency"))
                .allSatisfy(m -> assertThat(m.kind()).isEqualTo(ModelKind.ENUM));
        assertThat(parsed.getModels()).filteredOn(m -> m.name().equals("Payment"))
                .allSatisfy(m -> assertThat(m.kind()).isEqualTo(ModelKind.SCHEMA));
    }

    @Test
    void convertoSwagger2InOperazioniEModelli() {
        ParsedSpec parsed = parser.parse(SWAGGER_2, SpecFormat.OPENAPI_2);

        assertThat(parsed.getTitle()).isEqualTo("Legacy API");
        assertThat(parsed.getEndpoints()).hasSize(1);
        ParsedEndpoint endpoint = parsed.getEndpoints().get(0);
        assertThat(endpoint.getHttpMethod()).isEqualTo("GET");
        assertThat(endpoint.getPath()).isEqualTo("/items/{id}");
        assertThat(endpoint.getParametersJson()).contains("\"in\" : \"path\"");

        assertThat(parsed.getModels()).extracting(ParsedSpec.ParsedModel::name).contains("Item");
    }

    @Test
    void segnalaUnaSpecificaSenzaOperazioni() {
        ParsedSpec parsed = parser.parse("openapi: 3.0.3\ninfo:\n  title: vuota\n  version: '1'\npaths: {}\n",
                SpecFormat.OPENAPI_3);

        assertThat(parsed.getEndpoints()).isEmpty();
        assertThat(parsed.getWarnings()).anyMatch(w -> w.contains("non contiene operazioni"));
    }

    @Test
    void rifiutaUnContenutoNonValido() {
        assertThatThrownBy(() -> parser.parse("questo non è yaml valido: [[[", SpecFormat.OPENAPI_3))
                .isInstanceOf(BusinessException.class);
    }
}
