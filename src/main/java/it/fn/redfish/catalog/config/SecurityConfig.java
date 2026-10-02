package it.fn.redfish.catalog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import it.fn.redfish.catalog.domain.Perm;
import it.fn.redfish.catalog.security.CatalogUserDetails;
import it.fn.redfish.catalog.security.LoginSuccessHandler;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String P = CatalogUserDetails.PERMISSION_PREFIX;

    @Bean PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean SecurityFilterChain filterChain(HttpSecurity http,
                                           LoginSuccessHandler loginSuccessHandler) throws Exception {

        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();

        csrfHandler.setCsrfRequestAttributeName(null);

        http
                .authorizeHttpRequests(reg -> reg
                        .requestMatchers("/setup/**", "/login", "/error").permitAll()
                        .requestMatchers("/css/**", "/js/**", "/img/**", "/webjars/**", "/favicon.ico").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/branding/logo").permitAll()
                        .requestMatchers("/admin/users/**").hasAuthority(P + Perm.USER_MANAGE)
                        .requestMatchers("/admin/roles/**", "/admin/permissions/**").hasAuthority(P + Perm.PERMISSION_MANAGE)
                        .requestMatchers("/admin/settings/**").hasAuthority(P + Perm.SETTINGS_MANAGE)
                        .requestMatchers("/admin/audit/**").hasAnyAuthority(P + Perm.USER_MANAGE, P + Perm.PERMISSION_MANAGE)
                        .anyRequest().authenticated())

                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .successHandler(loginSuccessHandler)
                        .failureUrl("/login?error")
                        .permitAll())

                .logout(logout -> logout
                        .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID"))

                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfHandler))

                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.migrateSession())
                        .invalidSessionUrl("/login?expired"))

                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; img-src 'self' data:; "
                                        + "style-src 'self' 'unsafe-inline'; "
                                        + "script-src 'self' 'unsafe-inline'; "
                                        + "font-src 'self' data:; "
                                        + "connect-src 'self'; frame-ancestors 'self'")))

                .exceptionHandling(ex -> ex.accessDeniedPage("/error/403"));

        return http.build();
    }

    @Bean SavedRequestAwareAuthenticationSuccessHandler savedRequestHandler() {
        SavedRequestAwareAuthenticationSuccessHandler handler = new SavedRequestAwareAuthenticationSuccessHandler();
        handler.setDefaultTargetUrl("/");
        return handler;
    }
}
