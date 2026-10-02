package it.fn.redfish.catalog.web.form;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SetupForm {

    @NotBlank(message = "{message.the.application.name.is.required}")
    @Size(max = 120)
    private String appName = "API Catalog";

    @NotBlank(message = "{message.the.username.is.required}")
    @Size(max = 80)
    private String adminUsername = "admin";

    @NotBlank(message = "{message.the.name.is.required}")
    @Size(max = 120)
    private String adminFirstName;

    @NotBlank(message = "{message.the.last.name.is.required}")
    @Size(max = 120)
    private String adminLastName;

    @NotBlank(message = "{message.the.email.is.required}")
    @Email(message = "{message.invalid.email}")
    @Size(max = 200)
    private String adminEmail;

    @NotBlank(message = "{message.the.password.is.required}")
    @Size(min = 8, max = 100, message = "{message.the.password.must.contain.at.least.8.characters}")
    private String adminPassword;

    @NotBlank(message = "{message.confirm.the.password}")
    private String adminPasswordConfirm;

    private boolean seedExampleCatalog = true;

    public boolean passwordsMatch() {
        return adminPassword != null && adminPassword.equals(adminPasswordConfirm);
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getAdminUsername() {
        return adminUsername;
    }

    public void setAdminUsername(String adminUsername) {
        this.adminUsername = adminUsername;
    }

    public String getAdminFirstName() {
        return adminFirstName;
    }

    public void setAdminFirstName(String adminFirstName) {
        this.adminFirstName = adminFirstName;
    }

    public String getAdminLastName() {
        return adminLastName;
    }

    public void setAdminLastName(String adminLastName) {
        this.adminLastName = adminLastName;
    }

    public String getAdminEmail() {
        return adminEmail;
    }

    public void setAdminEmail(String adminEmail) {
        this.adminEmail = adminEmail;
    }

    public String getAdminPassword() {
        return adminPassword;
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }

    public String getAdminPasswordConfirm() {
        return adminPasswordConfirm;
    }

    public void setAdminPasswordConfirm(String adminPasswordConfirm) {
        this.adminPasswordConfirm = adminPasswordConfirm;
    }

    public boolean isSeedExampleCatalog() {
        return seedExampleCatalog;
    }

    public void setSeedExampleCatalog(boolean seedExampleCatalog) {
        this.seedExampleCatalog = seedExampleCatalog;
    }
}
