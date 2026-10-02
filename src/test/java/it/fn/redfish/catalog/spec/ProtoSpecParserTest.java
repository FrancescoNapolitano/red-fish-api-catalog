package it.fn.redfish.catalog.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import it.fn.redfish.catalog.domain.ModelKind;
import it.fn.redfish.catalog.support.BusinessException;

class ProtoSpecParserTest {
    @org.junit.jupiter.api.BeforeEach
    void useItalianMessages() {
        org.springframework.context.i18n.LocaleContextHolder.setLocale(java.util.Locale.ITALIAN);
    }

    @org.junit.jupiter.api.AfterEach
    void resetLocale() {
        org.springframework.context.i18n.LocaleContextHolder.resetLocaleContext();
    }


    private final ProtoSpecParser parser = new ProtoSpecParser();

    private static final String PROTO = """
            // Servizio di notifica multicanale.
            syntax = "proto3";

            package notification.v2;

            import "google/protobuf/timestamp.proto";

            // Canale di invio.
            enum Channel {
              CHANNEL_UNSPECIFIED = 0;
              EMAIL = 1;
              SMS = 2;
            }

            /* commento a blocco da ignorare */
            message SendRequest {
              // Destinatario.
              string recipient = 1;
              Channel channel = 2;
              repeated string attachments = 5;
              map<string, string> metadata = 6;
              int64 scheduled_at = 7;

              oneof priority {
                bool urgent = 8;
                int32 delay_seconds = 9;
              }
            }

            message SendResponse {
              string message_id = 1;
              bool accepted = 2;
            }

            service NotificationService {
              // Invia una notifica.
              rpc Send (SendRequest) returns (SendResponse);
              rpc SendBatch (stream SendRequest) returns (SendResponse);
              rpc Watch (SendRequest) returns (stream SendResponse);
              rpc Chat (stream SendRequest) returns (stream SendResponse);
            }
            """;

    @Test
    void usaIlPackageComeTitoloEDerivaLaVersione() {
        ParsedSpec parsed = parser.parse(PROTO);

        assertThat(parsed.getTitle()).isEqualTo("notification.v2");
        assertThat(parsed.getVersion()).isEqualTo("v2");
    }

    @Test
    void ricadeSullaSintassiSeIlPackageNonHaVersione() {
        ParsedSpec parsed = parser.parse("syntax = \"proto3\";\npackage plain;\nservice S { rpc M (A) returns (B); }\n");

        assertThat(parsed.getVersion()).isEqualTo("proto3");
    }

    @Test
    void estraeTuttiIMetodiRpcConIlPathQualificato() {
        ParsedSpec parsed = parser.parse(PROTO);

        assertThat(parsed.getEndpoints()).hasSize(4);
        assertThat(parsed.getEndpoints()).extracting(ParsedEndpoint::getPath).containsExactly(
                "/notification.v2.NotificationService/Send",
                "/notification.v2.NotificationService/SendBatch",
                "/notification.v2.NotificationService/Watch",
                "/notification.v2.NotificationService/Chat");
        assertThat(parsed.getEndpoints()).allSatisfy(e -> assertThat(e.getHttpMethod()).isEqualTo("RPC"));
    }

    @Test
    void classificaCorrettamenteLoStreaming() {
        ParsedSpec parsed = parser.parse(PROTO);

        assertThat(parsed.getEndpoints()).extracting(ParsedEndpoint::getGrpcStreaming)
                .containsExactly("UNARY", "CLIENT_STREAMING", "SERVER_STREAMING", "BIDIRECTIONAL");
    }

    @Test
    void associaITipiDiRichiestaERisposta() {
        ParsedEndpoint send = parser.parse(PROTO).getEndpoints().get(0);

        assertThat(send.getGrpcService()).isEqualTo("notification.v2.NotificationService");
        assertThat(send.getGrpcMethod()).isEqualTo("Send");
        assertThat(send.getGrpcRequestType()).isEqualTo("SendRequest");
        assertThat(send.getGrpcResponseType()).isEqualTo("SendResponse");
        assertThat(send.getSummary()).isEqualTo("Invia una notifica.");
        assertThat(send.getRequestBodyJson()).contains("#/components/schemas/SendRequest");
        assertThat(send.getResponsesJson()).contains("#/components/schemas/SendResponse");
    }

    @Test
    void estraeMessageEdEnumComeModelli() {
        ParsedSpec parsed = parser.parse(PROTO);

        assertThat(parsed.getModels()).extracting(ParsedSpec.ParsedModel::name)
                .containsExactlyInAnyOrder("Channel", "SendRequest", "SendResponse");
        assertThat(parsed.getModels()).filteredOn(m -> m.name().equals("Channel"))
                .allSatisfy(m -> {
                    assertThat(m.kind()).isEqualTo(ModelKind.ENUM);
                    assertThat(m.schemaJson()).contains("EMAIL").contains("SMS");
                });
        assertThat(parsed.getModels()).filteredOn(m -> m.name().equals("SendRequest"))
                .allSatisfy(m -> assertThat(m.kind()).isEqualTo(ModelKind.MESSAGE));
    }

    @Test
    void normalizzaITipiDeiCampi() {
        String schema = parser.parse(PROTO).getModels().stream()
                .filter(m -> m.name().equals("SendRequest")).findFirst().orElseThrow().schemaJson();

        assertThat(schema).contains("\"recipient\"").contains("\"type\" : \"string\"");

        assertThat(schema).contains("\"attachments\"").contains("\"type\" : \"array\"");

        assertThat(schema).contains("\"metadata\"").contains("additionalPropertiesType");

        assertThat(schema).contains("\"scheduled_at\"").contains("\"format\" : \"int64\"");

        assertThat(schema).contains("#/components/schemas/Channel");

        assertThat(schema).contains("\"fieldNumber\" : 1");
    }

    @Test
    void iCampiDiUnOneofAppartengonoAlMessageContenitore() {
        String schema = parser.parse(PROTO).getModels().stream()
                .filter(m -> m.name().equals("SendRequest")).findFirst().orElseThrow().schemaJson();

        assertThat(schema).contains("\"urgent\"").contains("\"delay_seconds\"");
        assertThat(schema).contains("\"oneof\" : \"priority\"");
    }

    @Test
    void segnalaGliImportNonRisolti() {
        ParsedSpec parsed = parser.parse(PROTO);

        assertThat(parsed.getWarnings()).anyMatch(w -> w.contains("google/protobuf/timestamp.proto"));
    }

    @Test
    void segnalaLAssenzaDiServizi() {
        ParsedSpec parsed = parser.parse("syntax = \"proto3\";\npackage a;\nmessage M { string x = 1; }\n");

        assertThat(parsed.getEndpoints()).isEmpty();
        assertThat(parsed.getWarnings()).anyMatch(w -> w.contains("Nessun blocco 'service'"));
    }

    @Test
    void rifiutaUnFileVuoto() {
        assertThatThrownBy(() -> parser.parse("  "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("vuoto");
    }

    @Test
    void qualificaIMessageAnnidati() {
        String proto = """
                syntax = "proto3";
                package a.v1;
                message Outer {
                  string id = 1;
                  message Inner {
                    string value = 1;
                  }
                }
                service S { rpc M (Outer) returns (Outer); }
                """;

        ParsedSpec parsed = parser.parse(proto);
        assertThat(parsed.getModels()).extracting(ParsedSpec.ParsedModel::name)
                .contains("Outer", "Outer.Inner");
    }
}
