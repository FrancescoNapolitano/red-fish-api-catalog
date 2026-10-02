package it.fn.redfish.catalog.support;

import java.util.Locale;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

public final class I18n {
    private static final ResourceBundleMessageSource MESSAGES = new ResourceBundleMessageSource();

    static {
        MESSAGES.setBasename("messages");
        MESSAGES.setDefaultEncoding("UTF-8");
        MESSAGES.setFallbackToSystemLocale(false);
        MESSAGES.setDefaultLocale(Locale.ENGLISH);
    }

    private I18n() {
    }

    public static String text(String key, Object... arguments) {
        Locale locale = LocaleContextHolder.getLocaleContext() == null ? Locale.ENGLISH : LocaleContextHolder.getLocale();
        return MESSAGES.getMessage(key, arguments.length == 0 ? null : arguments, locale);
    }
}
