package it.fn.redfish.catalog.spec;

import it.fn.redfish.catalog.support.I18n;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import it.fn.redfish.catalog.domain.SpecFormat;
import it.fn.redfish.catalog.support.BusinessException;

@Component
public class SpecFormatDetector {

    private static final Pattern PROTO_SYNTAX = Pattern.compile("(?m)^\\s*syntax\\s*=\\s*[\"']proto[23][\"']\\s*;");
    private static final Pattern PROTO_SERVICE = Pattern.compile("(?m)^\\s*service\\s+\\w+\\s*\\{");

    private static final Pattern OPENAPI_3 = Pattern.compile("[\"']?openapi[\"']?\\s*:\\s*[\"']?3");
    private static final Pattern OPENAPI_2 = Pattern.compile("[\"']?swagger[\"']?\\s*:\\s*[\"']?2");

    public SpecFormat detect(String fileName, String content) {
        return tryDetect(fileName, content).orElseThrow(() -> new BusinessException(
                I18n.text("message.unrecognized.format.expected.openapi.2.0.3.x.json.or.yaml.or.a.proto.f")));
    }

    public Optional<SpecFormat> tryDetect(String fileName, String content) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        String body = content == null ? "" : content;

        if (name.endsWith(".proto") || PROTO_SYNTAX.matcher(body).find()
                || (PROTO_SERVICE.matcher(body).find() && body.contains("rpc "))) {
            return Optional.of(SpecFormat.PROTO);
        }
        if (OPENAPI_3.matcher(body).find()) {
            return Optional.of(SpecFormat.OPENAPI_3);
        }
        if (OPENAPI_2.matcher(body).find()) {
            return Optional.of(SpecFormat.OPENAPI_2);
        }
        return Optional.empty();
    }
}
