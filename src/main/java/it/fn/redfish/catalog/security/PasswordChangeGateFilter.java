package it.fn.redfish.catalog.security;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class PasswordChangeGateFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof CatalogUserDetails user)
                || !user.isMustChangePassword()) {
            chain.doFilter(request, response);
            return;
        }

        String path = pathOf(request);
        if (isAllowed(path)) {
            chain.doFilter(request, response);
            return;
        }

        response.sendRedirect(request.getContextPath() + "/account/password?required");
    }

    private boolean isAllowed(String path) {
        return path.equals("/account/password")
                || path.equals("/logout")
                || path.equals("/login")
                || path.equals("/error")
                || path.startsWith("/error/")
                || path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.startsWith("/img/")
                || path.startsWith("/webjars/")
                || path.startsWith("/branding/")
                || path.startsWith("/actuator/")
                || path.equals("/favicon.ico");
    }

    private String pathOf(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        return context != null && !context.isEmpty() && uri.startsWith(context)
                ? uri.substring(context.length())
                : uri;
    }
}
