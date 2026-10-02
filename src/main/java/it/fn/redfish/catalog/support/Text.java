package it.fn.redfish.catalog.support;

public final class Text {

    private static final int BOM = 0xFEFF;

    private Text() {
    }

    public static String stripBom(String content) {
        if (content == null || content.isEmpty() || content.charAt(0) != (char) BOM) {
            return content;
        }
        return content.substring(1);
    }

    public static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 1) + "…";
    }

    public static char firstNonWhitespace(String value) {
        if (value == null) {
            return 0;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!Character.isWhitespace(c)) {
                return c;
            }
        }
        return 0;
    }
}
