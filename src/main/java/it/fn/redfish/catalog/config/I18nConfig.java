package it.fn.redfish.catalog.config;

import java.time.Duration;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

@Configuration
public class I18nConfig implements WebMvcConfigurer {
    @Bean
    public LocaleResolver localeResolver() {
        var resolver = new CookieLocaleResolver("catalog-language") {
            @Override
            public org.springframework.context.i18n.LocaleContext resolveLocaleContext(jakarta.servlet.http.HttpServletRequest request) {
                var context = super.resolveLocaleContext(request);
                return () -> {
                    Locale locale = context.getLocale();
                    return locale != null && "it".equals(locale.getLanguage()) ? Locale.ITALIAN : Locale.ENGLISH;
                };
            }
        };
        resolver.setDefaultLocale(Locale.ENGLISH);
        resolver.setCookieMaxAge(Duration.ofDays(365));
        resolver.setCookieHttpOnly(true);
        resolver.setCookieSameSite("Lax");
        resolver.setRejectInvalidCookies(false);
        return resolver;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        var interceptor = new LocaleChangeInterceptor() {
            @Override
            protected Locale parseLocaleValue(String value) {
                return "it".equalsIgnoreCase(value) ? Locale.ITALIAN : Locale.ENGLISH;
            }
        };
        interceptor.setParamName("lang");
        registry.addInterceptor(interceptor);
    }

    @Bean
    public LocalValidatorFactoryBean defaultValidator(MessageSource messageSource) {
        var validator = new LocalValidatorFactoryBean();
        validator.setValidationMessageSource(messageSource);
        return validator;
    }
}
