package it.fn.redfish.catalog.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import it.fn.redfish.catalog.domain.AppUser;

public class CatalogUserDetails implements UserDetails {

    public static final String PERMISSION_PREFIX = "PERM_";

    private final Long id;
    private final String username;
    private final String password;
    private final String displayName;
    private final boolean enabled;
    private final boolean mustChangePassword;
    private final boolean admin;
    private final Set<String> permissions;
    private final Collection<GrantedAuthority> authorities;

    public CatalogUserDetails(AppUser user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.password = user.getPasswordHash();
        this.displayName = user.getDisplayName();
        this.enabled = user.isEnabled();
        this.mustChangePassword = user.isMustChangePassword();
        this.admin = user.isAdmin();
        this.permissions = Collections.unmodifiableSet(new LinkedHashSet<>(user.effectivePermissions()));

        Collection<GrantedAuthority> list = new ArrayList<>();
        user.getRoles().forEach(r -> list.add(new SimpleGrantedAuthority("ROLE_" + r.getName().toUpperCase())));
        this.permissions.forEach(p -> list.add(new SimpleGrantedAuthority(PERMISSION_PREFIX + p)));
        this.authorities = Collections.unmodifiableCollection(list);
    }

    public Long getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public boolean isAdmin() {
        return admin;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
