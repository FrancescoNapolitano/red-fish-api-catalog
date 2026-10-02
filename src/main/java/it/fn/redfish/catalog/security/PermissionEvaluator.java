package it.fn.redfish.catalog.security;

import it.fn.redfish.catalog.support.I18n;
import java.util.List;
import java.util.Optional;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import it.fn.redfish.catalog.domain.ApiGroup;
import it.fn.redfish.catalog.domain.ApiService;
import it.fn.redfish.catalog.repo.ApiGroupRepository;
import it.fn.redfish.catalog.repo.UserGroupPermissionRepository;

@Component("perm")
public class PermissionEvaluator {

    private final UserGroupPermissionRepository groupPermissions;
    private final ApiGroupRepository groups;

    public PermissionEvaluator(UserGroupPermissionRepository groupPermissions, ApiGroupRepository groups) {
        this.groupPermissions = groupPermissions;
        this.groups = groups;
    }

    public Optional<CatalogUserDetails> currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }
        return auth.getPrincipal() instanceof CatalogUserDetails cud ? Optional.of(cud) : Optional.empty();
    }

    public Long currentUserId() {
        return currentUser().map(CatalogUserDetails::getId).orElse(null);
    }

    public boolean has(String code) {
        return currentUser()
                .map(u -> u.isAdmin() || u.getPermissions().contains(code))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean hasAnywhere(String code) {
        if (has(code)) {
            return true;
        }
        Long userId = currentUserId();
        return userId != null && !groupPermissions.findGrantedPaths(userId, code).isEmpty();
    }

    @Transactional(readOnly = true)
    public boolean hasOnPath(String code, String groupPath) {
        if (has(code)) {
            return true;
        }
        Long userId = currentUserId();
        if (userId == null || groupPath == null) {
            return false;
        }
        return groupPermissions.countGrant(userId, code, groupPath) > 0;
    }

    @Transactional(readOnly = true)
    public boolean hasOnGroup(String code, Long groupId) {
        if (has(code)) {
            return true;
        }
        if (groupId == null) {
            return false;
        }
        return groups.findById(groupId).map(g -> hasOnPath(code, g.getPath())).orElse(false);
    }

    public boolean hasOnGroup(String code, ApiGroup group) {
        return group == null ? has(code) : hasOnPath(code, group.getPath());
    }

    public boolean hasOnService(String code, ApiService service) {
        return service == null ? has(code) : hasOnPath(code, service.getGroup().getPath());
    }

    @Transactional(readOnly = true)
    public List<String> grantedPaths(String code) {
        Long userId = currentUserId();
        return userId == null ? List.of() : groupPermissions.findGrantedPaths(userId, code);
    }

    public void require(String code) {
        if (!has(code)) {
            throw new AccessDeniedException(I18n.text("message.missing.permission") + code);
        }
    }

    public void requireOnGroup(String code, ApiGroup group) {
        if (!hasOnGroup(code, group)) {
            throw new AccessDeniedException(I18n.text("message.missing.permission") + code);
        }
    }

    public void requireOnService(String code, ApiService service) {
        if (!hasOnService(code, service)) {
            throw new AccessDeniedException(I18n.text("message.missing.permission") + code);
        }
    }
}
