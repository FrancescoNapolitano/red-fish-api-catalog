package it.fn.redfish.catalog.support;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class Slugs {

    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_DASH = Pattern.compile("(^-+)|(-+$)");

    private Slugs() {
    }

    public static String of(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase(Locale.ROOT);
        String slug = EDGE_DASH.matcher(NON_ALNUM.matcher(normalized).replaceAll("-")).replaceAll("");
        return slug.length() > 160 ? slug.substring(0, 160) : slug;
    }

    public static String orFallback(String input, String fallback) {
        String slug = of(input);
        return slug.isEmpty() ? fallback : slug;
    }
}
