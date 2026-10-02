package it.fn.redfish.catalog.security;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import it.fn.redfish.catalog.service.SettingsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class SetupGateFilter extends OncePerRequestFilter {

    private final SettingsService settings;

    public SetupGateFilter(SettingsService settings) {
        this.settings = settings;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();
        String context = request.getContextPath();
        String path = context != null && !context.isEmpty() && uri.startsWith(context)
                ? uri.substring(context.length())
                : uri;

        if (isAlwaysAllowed(path)) {
            chain.doFilter(request, response);
            return;
        }

        boolean completed = settings.isSetupCompleted();
        boolean setupRequest = path.equals("/setup") || path.startsWith("/setup/");

        if (!completed && !setupRequest) {
            redirect(request, response, "/setup");
            return;
        }
        if (completed && setupRequest) {
            redirect(request, response, "/");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isAlwaysAllowed(String path) {
        return path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.startsWith("/img/")
                || path.startsWith("/webjars/")
                || path.startsWith("/actuator/")
                || path.equals("/favicon.ico")
                || path.equals("/error")
                || path.startsWith("/error/");
    }

    private void redirect(HttpServletRequest request, HttpServletResponse response, String target) throws IOException {
        response.sendRedirect(request.getContextPath() + target);
    }
}
