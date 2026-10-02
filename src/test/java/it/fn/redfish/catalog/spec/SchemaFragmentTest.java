package it.fn.redfish.catalog.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.StringTemplateResolver;

class SchemaFragmentTest {

    private static final Pattern FIELD_CELL = Pattern.compile(
            "<span class=\"rf-mono\"[^>]*>([^<]+)</span>");

    private static final Pattern INDENT_CELL = Pattern.compile(
            "<span style=\"padding-left:([0-9.]+)rem\"");

    private final SchemaRenderer renderer = new SchemaRenderer();
    private final SpringTemplateEngine engine = engine();

    private static SpringTemplateEngine engine() {
        ClassLoaderTemplateResolver templates = new ClassLoaderTemplateResolver();
        templates.setPrefix("templates/");
        templates.setSuffix(".html");
        templates.setTemplateMode(TemplateMode.HTML);
        templates.setCharacterEncoding("UTF-8");
        templates.setOrder(2);

        StringTemplateResolver inline = new StringTemplateResolver();
        inline.setTemplateMode(TemplateMode.HTML);
        inline.setResolvablePatterns(Set.of("<*"));
        inline.setOrder(1);

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.addTemplateResolver(inline);
        engine.addTemplateResolver(templates);
        return engine;
    }

    private String render(String schemaJson) {
        Context context = new Context();
        context.setVariable("schema", renderer.toView(schemaJson, Map.of()));
        return engine.process(
                "<th:block th:replace=\"~{fragments/schema :: tree(${schema})}\"></th:block>", context);
    }

    private List<String> fields(String html) {
        List<String> names = new ArrayList<>();
        Matcher m = FIELD_CELL.matcher(html);
        while (m.find()) {
            names.add(m.group(1).trim());
        }
        return names;
    }

    private List<String> indents(String html) {
        List<String> values = new ArrayList<>();
        Matcher m = INDENT_CELL.matcher(html);
        while (m.find()) {
            values.add(m.group(1));
        }
        return values;
    }

    @Test
    void iCampiDiUnOggettoSeguonoSubitoLOggettoCheLiContiene() {
        String html = render("""
                {
                  "type": "object",
                  "properties": {
                    "persona": {
                      "type": "object",
                      "properties": {
                        "nome": { "type": "string" },
                        "cognome": { "type": "string" }
                      }
                    },
                    "citta": { "type": "string" },
                    "lavoro": { "type": "string" }
                  }
                }
                """);

        assertThat(fields(html))
                .containsExactly("persona", "nome", "cognome", "citta", "lavoro");
    }

    @Test
    void ogniLivelloDiAnnidamentoRientraDiUnPasso() {
        String html = render("""
                {
                  "type": "object",
                  "properties": {
                    "persona": {
                      "type": "object",
                      "properties": {
                        "indirizzo": {
                          "type": "object",
                          "properties": { "via": { "type": "string" } }
                        }
                      }
                    },
                    "citta": { "type": "string" }
                  }
                }
                """);

        assertThat(fields(html)).containsExactly("persona", "indirizzo", "via", "citta");
        assertThat(indents(html)).containsExactly("0.0", "1.1", "2.2", "0.0");
    }

    @Test
    void oggettiFratelliNonSiMescolano() {
        String html = render("""
                {
                  "type": "object",
                  "properties": {
                    "debitore": {
                      "type": "object",
                      "properties": { "iban": { "type": "string" } }
                    },
                    "creditore": {
                      "type": "object",
                      "properties": { "iban": { "type": "string" } }
                    }
                  }
                }
                """);

        assertThat(fields(html)).containsExactly("debitore", "iban", "creditore", "iban");
    }
}
