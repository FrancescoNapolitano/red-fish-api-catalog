package it.fn.redfish.catalog.security;

import it.fn.redfish.catalog.support.I18n;
import java.io.IOException;
import java.time.Instant;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import it.fn.redfish.catalog.repo.AppUserRepository;
import it.fn.redfish.catalog.service.AuditService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final AppUserRepository users;
    private final AuditService audit;

    public LoginSuccessHandler(AppUserRepository users, AuditService audit) {
        this.users = users;
        this.audit = audit;
        setDefaultTargetUrl("/");
        setAlwaysUseDefaultTargetUrl(false);
    }

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        users.findByUsernameIgnoreCase(authentication.getName()).ifPresent(u -> {
            u.setLastLoginAt(Instant.now());
            users.save(u);
        });
        audit.record("LOGIN", "AppUser", authentication.getName(), I18n.text("message.signed.in"));
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
