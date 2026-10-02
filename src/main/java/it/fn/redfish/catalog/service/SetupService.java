package it.fn.redfish.catalog.service;

import it.fn.redfish.catalog.support.I18n;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.fn.redfish.catalog.domain.AppRole;
import it.fn.redfish.catalog.domain.AppUser;
import it.fn.redfish.catalog.repo.AppRoleRepository;
import it.fn.redfish.catalog.repo.AppUserRepository;
import it.fn.redfish.catalog.support.BusinessException;
import it.fn.redfish.catalog.web.form.GroupForm;
import it.fn.redfish.catalog.web.form.SetupForm;
import it.fn.redfish.catalog.web.form.UserForm;

@Service
public class SetupService {

    private final SettingsService settings;
    private final UserService userService;
    private final AppUserRepository users;
    private final AppRoleRepository roles;
    private final GroupService groupService;
    private final AuditService audit;

    public SetupService(SettingsService settings, UserService userService, AppUserRepository users,
                        AppRoleRepository roles, GroupService groupService, AuditService audit) {
        this.settings = settings;
        this.userService = userService;
        this.users = users;
        this.roles = roles;
        this.groupService = groupService;
        this.audit = audit;
    }

    public boolean isCompleted() {
        return settings.isSetupCompleted();
    }

    @Transactional(readOnly = true)
    public boolean isFirstRun() {
        return !isCompleted() && users.count() == 0;
    }

    @Transactional
    public AppUser complete(SetupForm form) {
        if (isCompleted()) {
            throw new BusinessException(I18n.text("message.initial.setup.has.already.been.completed"));
        }
        if (!form.passwordsMatch()) {
            throw new BusinessException(I18n.text("message.the.passwords.do.not.match"));
        }
        AppRole admin = roles.findByNameIgnoreCase("ADMIN")
                .orElseThrow(() -> new BusinessException(I18n.text("message.admin.role.not.found.incomplete.database.migration")));

        UserForm userForm = new UserForm();
        userForm.setUsername(form.getAdminUsername().trim());
        userForm.setFirstName(form.getAdminFirstName());
        userForm.setLastName(form.getAdminLastName());
        userForm.setEmail(form.getAdminEmail());
        userForm.setPassword(form.getAdminPassword());
        userForm.setEnabled(true);
        userForm.setRoleIds(Set.of(admin.getId()));
        AppUser created = userService.create(userForm);

        settings.put(SettingsService.APP_NAME, form.getAppName().trim());

        if (form.isSeedExampleCatalog()) {
            seedExampleCatalog();
        }

        settings.put(SettingsService.SETUP_COMPLETED, "true");
        audit.record("SETUP_COMPLETE", "AppSetting", SettingsService.SETUP_COMPLETED,
                I18n.text("message.initial.setup.completed.administrator") + created.getUsername());
        return created;
    }

    private void seedExampleCatalog() {
        Map<String, List<String>> structure = Map.of(
                "Finance", List.of("Payments"),
                "Notification", List.of("Email", "SMS"),
                "Identity", List.of("OAuth", "SAML"));

        List<String> order = List.of("Finance", "Notification", "Identity");
        int rootOrder = 0;
        for (String rootName : order) {
            GroupForm rootForm = new GroupForm();
            rootForm.setName(rootName);
            rootForm.setSortOrder(rootOrder++);
            Long rootId = groupService.create(rootForm).getId();

            int childOrder = 0;
            for (String childName : structure.get(rootName)) {
                GroupForm childForm = new GroupForm();
                childForm.setName(childName);
                childForm.setParentId(rootId);
                childForm.setSortOrder(childOrder++);
                Long childId = groupService.create(childForm).getId();

                if ("Payments".equals(childName)) {
                    int leafOrder = 0;
                    for (String leaf : List.of("SEPA", "PSD2")) {
                        GroupForm leafForm = new GroupForm();
                        leafForm.setName(leaf);
                        leafForm.setParentId(childId);
                        leafForm.setSortOrder(leafOrder++);
                        groupService.create(leafForm);
                    }
                }
            }
        }
    }
}
