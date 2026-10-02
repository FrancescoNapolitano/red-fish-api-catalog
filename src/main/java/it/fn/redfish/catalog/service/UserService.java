package it.fn.redfish.catalog.service;

import it.fn.redfish.catalog.support.I18n;
import java.security.SecureRandom;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.fn.redfish.catalog.domain.ApiGroup;
import it.fn.redfish.catalog.domain.AppRole;
import it.fn.redfish.catalog.domain.AppUser;
import it.fn.redfish.catalog.domain.UserGroupPermission;
import it.fn.redfish.catalog.repo.ApiGroupRepository;
import it.fn.redfish.catalog.repo.AppRoleRepository;
import it.fn.redfish.catalog.repo.AppUserRepository;
import it.fn.redfish.catalog.repo.UserGroupPermissionRepository;
import it.fn.redfish.catalog.support.BusinessException;
import it.fn.redfish.catalog.support.NotFoundException;
import it.fn.redfish.catalog.web.form.UserForm;

@Service
public class UserService {

    private static final String PASSWORD_ALPHABET =
            "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789!@#$%";
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final AppUserRepository users;
    private final AppRoleRepository roles;
    private final ApiGroupRepository groups;
    private final UserGroupPermissionRepository groupPermissions;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final SecureRandom random = new SecureRandom();

    public UserService(AppUserRepository users, AppRoleRepository roles, ApiGroupRepository groups,
                       UserGroupPermissionRepository groupPermissions, PasswordEncoder passwordEncoder,
                       AuditService audit) {
        this.users = users;
        this.roles = roles;
        this.groups = groups;
        this.groupPermissions = groupPermissions;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<AppUser> findAll() {
        return users.findAllByOrderByUsernameAsc();
    }

    @Transactional(readOnly = true)
    public AppUser get(Long id) {
        return users.findById(id).orElseThrow(() -> NotFoundException.of(I18n.text("ui.user"), id));
    }

    @Transactional(readOnly = true)
    public AppUser getByUsername(String username) {
        return users.findByUsernameFetchRoles(username)
                .orElseThrow(() -> NotFoundException.of(I18n.text("ui.user"), username));
    }

    @Transactional
    public AppUser create(UserForm form) {
        if (users.existsByUsernameIgnoreCase(form.getUsername())) {
            throw new BusinessException(I18n.text("message.username.already.in.use") + form.getUsername());
        }
        String rawPassword = form.getPassword();
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new BusinessException(I18n.text("message.the.initial.password.is.required"));
        }
        validatePassword(rawPassword);

        AppUser user = new AppUser(form.getUsername().trim(), passwordEncoder.encode(rawPassword));
        applyProfile(user, form);
        user.setRoles(resolveRoles(form.getRoleIds()));
        AppUser saved = users.save(user);
        audit.record("USER_CREATE", "AppUser", saved.getId(),
                I18n.text("message.created.user") + saved.getUsername() + I18n.text("message.with.roles") + saved.getRoleNames() + "]");
        return saved;
    }

    @Transactional
    public AppUser update(Long id, UserForm form) {
        AppUser user = get(id);
        if (!user.getUsername().equalsIgnoreCase(form.getUsername())
                && users.existsByUsernameIgnoreCase(form.getUsername())) {
            throw new BusinessException(I18n.text("message.username.already.in.use") + form.getUsername());
        }
        boolean wasAdmin = user.isAdmin();
        user.setUsername(form.getUsername().trim());
        applyProfile(user, form);
        user.setRoles(resolveRoles(form.getRoleIds()));

        if (wasAdmin && !user.isAdmin()) {
            guardLastAdmin(user.getId());
        }
        if (!form.isEnabled()) {
            guardLastAdminIfAdmin(user);
        }
        AppUser saved = users.save(user);
        audit.record("USER_UPDATE", "AppUser", saved.getId(),
                I18n.text("message.updated.user") + saved.getUsername() + I18n.text("message.roles") + saved.getRoleNames() + I18n.text("message.active") + saved.isEnabled());
        return saved;
    }

    @Transactional
    public void setEnabled(Long id, boolean enabled) {
        AppUser user = get(id);
        if (!enabled) {
            guardLastAdminIfAdmin(user);
        }
        user.setEnabled(enabled);
        users.save(user);
        audit.record(enabled ? "USER_ENABLE" : "USER_DISABLE", "AppUser", id,
                (enabled ? I18n.text("message.enabled") : I18n.text("ui.disabled")) + I18n.text("message.user") + user.getUsername());
    }

    @Transactional
    public void delete(Long id) {
        AppUser user = get(id);
        guardLastAdminIfAdmin(user);
        groupPermissions.deleteByUserId(id);
        users.delete(user);
        audit.record("USER_DELETE", "AppUser", id, I18n.text("message.deleted.user") + user.getUsername());
    }

    @Transactional
    public String resetPassword(Long id, String newPassword, boolean mustChange) {
        AppUser user = get(id);
        String password = newPassword == null || newPassword.isBlank() ? generatePassword() : newPassword;
        validatePassword(password);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setMustChangePassword(mustChange);
        users.save(user);
        audit.record("USER_PASSWORD_RESET", "AppUser", id, I18n.text("message.reset.password.for") + user.getUsername());
        return password;
    }

    @Transactional
    public void changeOwnPassword(String username, String currentPassword, String newPassword) {
        AppUser user = getByUsername(username);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BusinessException(I18n.text("message.the.current.password.is.incorrect"));
        }
        validatePassword(newPassword);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        users.save(user);
        audit.record("USER_PASSWORD_CHANGE", "AppUser", user.getId(), I18n.text("message.password.changed.by.user"));
    }

    @Transactional(readOnly = true)
    public List<UserGroupPermission> groupPermissions(Long userId) {
        return groupPermissions.findByUser(userId);
    }

    @Transactional
    public void addGroupPermission(Long userId, Long groupId, String permissionCode) {
        AppUser user = get(userId);
        ApiGroup group = groups.findById(groupId).orElseThrow(() -> NotFoundException.of(I18n.text("ui.group"), groupId));
        boolean exists = groupPermissions.findByUser(userId).stream()
                .anyMatch(p -> p.getGroup().getId().equals(groupId) && p.getPermissionCode().equals(permissionCode));
        if (exists) {
            return;
        }
        groupPermissions.save(new UserGroupPermission(user, group, permissionCode));
        audit.record("USER_GROUP_PERMISSION_GRANT", "AppUser", userId,
                I18n.text("message.granted") + permissionCode + I18n.text("message.on") + group.getPath() + I18n.text("message.to") + user.getUsername());
    }

    @Transactional
    public void removeGroupPermission(Long userId, Long assignmentId) {
        groupPermissions.findById(assignmentId).ifPresent(p -> {
            if (!p.getUser().getId().equals(userId)) {
                throw new BusinessException(I18n.text("message.the.assignment.does.not.belong.to.this.user"));
            }
            groupPermissions.delete(p);
            audit.record("USER_GROUP_PERMISSION_REVOKE", "AppUser", userId,
                    I18n.text("message.revoked") + p.getPermissionCode() + I18n.text("message.on") + p.getGroup().getPath());
        });
    }

    private void applyProfile(AppUser user, UserForm form) {
        user.setFirstName(trim(form.getFirstName()));
        user.setLastName(trim(form.getLastName()));
        user.setEmail(trim(form.getEmail()));
        user.setEnabled(form.isEnabled());
        user.setMustChangePassword(form.isMustChangePassword());
    }

    private Set<AppRole> resolveRoles(Set<Long> roleIds) {
        Set<AppRole> resolved = new LinkedHashSet<>();
        if (roleIds != null) {
            roleIds.stream().filter(java.util.Objects::nonNull).forEach(rid ->
                    resolved.add(roles.findById(rid).orElseThrow(() -> NotFoundException.of(I18n.text("message.role"), rid))));
        }
        if (resolved.isEmpty()) {
            resolved.add(roles.findByNameIgnoreCase("VIEWER")
                    .orElseThrow(() -> new BusinessException(I18n.text("message.viewer.role.not.found"))));
        }
        return resolved;
    }

    private void validatePassword(String password) {
        if (password == null || password.trim().length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessException(I18n.text("message.the.password.must.contain.at.least") + MIN_PASSWORD_LENGTH + I18n.text("message.characters"));
        }
    }

    private void guardLastAdminIfAdmin(AppUser user) {
        if (user.isAdmin()) {
            guardLastAdmin(user.getId());
        }
    }

    private void guardLastAdmin(Long excludedUserId) {
        long remaining = users.findAll().stream()
                .filter(u -> !u.getId().equals(excludedUserId))
                .filter(AppUser::isEnabled)
                .filter(AppUser::isAdmin)
                .count();
        if (remaining == 0) {
            throw new BusinessException(I18n.text("message.operation.not.allowed.at.least.one.active.administrator.must.remain"));
        }
    }

    public String generatePassword() {
        StringBuilder sb = new StringBuilder(14);
        for (int i = 0; i < 14; i++) {
            sb.append(PASSWORD_ALPHABET.charAt(random.nextInt(PASSWORD_ALPHABET.length())));
        }
        return sb.toString();
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
