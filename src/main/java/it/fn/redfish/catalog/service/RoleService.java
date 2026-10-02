package it.fn.redfish.catalog.service;

import it.fn.redfish.catalog.support.I18n;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.fn.redfish.catalog.domain.AppRole;
import it.fn.redfish.catalog.domain.Permission;
import it.fn.redfish.catalog.repo.AppRoleRepository;
import it.fn.redfish.catalog.repo.AppUserRepository;
import it.fn.redfish.catalog.repo.PermissionRepository;
import it.fn.redfish.catalog.support.BusinessException;
import it.fn.redfish.catalog.support.NotFoundException;

@Service
public class RoleService {

    private final AppRoleRepository roles;
    private final PermissionRepository permissions;
    private final AppUserRepository users;
    private final AuditService audit;

    public RoleService(AppRoleRepository roles, PermissionRepository permissions, AppUserRepository users,
                       AuditService audit) {
        this.roles = roles;
        this.permissions = permissions;
        this.users = users;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<AppRole> findAll() {
        return roles.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public AppRole get(Long id) {
        return roles.findById(id).orElseThrow(() -> NotFoundException.of(I18n.text("message.role"), id));
    }

    @Transactional(readOnly = true)
    public List<Permission> allPermissions() {
        return permissions.findAllByOrderBySortOrderAsc();
    }

    @Transactional(readOnly = true)
    public List<Permission> groupScopedPermissions() {
        return permissions.findByGroupScopedTrueOrderBySortOrderAsc();
    }

    @Transactional(readOnly = true)
    public Map<String, List<Permission>> permissionsByCategory() {
        Map<String, List<Permission>> byCategory = new LinkedHashMap<>();
        for (Permission p : allPermissions()) {
            byCategory.computeIfAbsent(p.getCategory(), k -> new java.util.ArrayList<>()).add(p);
        }
        return byCategory;
    }

    @Transactional
    public AppRole create(String name, String description, Set<String> permissionCodes) {
        String normalized = name == null ? "" : name.trim().toUpperCase();
        if (normalized.isEmpty()) {
            throw new BusinessException(I18n.text("message.the.role.name.is.required"));
        }
        if (roles.existsByNameIgnoreCase(normalized)) {
            throw new BusinessException(I18n.text("message.role.already.exists") + normalized);
        }
        AppRole role = new AppRole(normalized, description);
        role.setPermissions(validCodes(permissionCodes));
        AppRole saved = roles.save(role);
        audit.record("ROLE_CREATE", "AppRole", saved.getId(), I18n.text("message.created.role") + normalized);
        return saved;
    }

    @Transactional
    public AppRole updatePermissions(Long id, String description, Set<String> permissionCodes) {
        AppRole role = get(id);
        Set<String> codes = validCodes(permissionCodes);
        if ("ADMIN".equalsIgnoreCase(role.getName()) && !codes.containsAll(adminMandatory())) {
            throw new BusinessException(I18n.text("message.the.admin.role.must.retain.administration.permissions"));
        }
        role.setDescription(description);
        role.setPermissions(codes);
        AppRole saved = roles.save(role);
        audit.record("ROLE_UPDATE", "AppRole", id,
                I18n.text("message.role.permissions") + role.getName() + " → [" + saved.getPermissionSummary() + "]");
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        AppRole role = get(id);
        if (role.isSystemRole()) {
            throw new BusinessException(I18n.text("message.system.roles.cannot.be.deleted"));
        }
        boolean inUse = users.findAll().stream().anyMatch(u -> u.getRoles().stream()
                .anyMatch(r -> r.getId().equals(id)));
        if (inUse) {
            throw new BusinessException(I18n.text("message.this.role.is.assigned.to.at.least.one.user.remove.the.assignments.firs"));
        }
        roles.delete(role);
        audit.record("ROLE_DELETE", "AppRole", id, I18n.text("message.deleted.role") + role.getName());
    }

    private Set<String> adminMandatory() {
        return Set.of("USER_MANAGE", "PERMISSION_MANAGE", "SETTINGS_MANAGE");
    }

    private Set<String> validCodes(Set<String> codes) {
        Set<String> known = new LinkedHashSet<>();
        permissions.findAll().forEach(p -> known.add(p.getCode()));
        Set<String> result = new LinkedHashSet<>();
        if (codes != null) {
            codes.stream().filter(known::contains).forEach(result::add);
        }
        return result;
    }
}
