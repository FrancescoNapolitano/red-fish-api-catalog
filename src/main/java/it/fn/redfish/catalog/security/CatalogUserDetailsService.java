package it.fn.redfish.catalog.security;

import it.fn.redfish.catalog.support.I18n;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.fn.redfish.catalog.repo.AppUserRepository;

@Service
public class CatalogUserDetailsService implements UserDetailsService {

    private final AppUserRepository users;

    public CatalogUserDetailsService(AppUserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return users.findByUsernameFetchRoles(username)
                .map(CatalogUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException(I18n.text("message.user.not.found") + username));
    }
}
