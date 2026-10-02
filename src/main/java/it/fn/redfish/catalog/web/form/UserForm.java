package it.fn.redfish.catalog.web.form;

import java.util.LinkedHashSet;
import java.util.Set;

import it.fn.redfish.catalog.domain.AppUser;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class UserForm {

    private Long id;

    @NotBlank(message = "{message.the.username.is.required}")
    @Size(max = 80)
    @Pattern(regexp = "^[A-Za-z0-9._@-]+$", message = "{message.letters.digits.and.are.allowed}")
    private String username;

    @NotBlank(message = "{message.the.name.is.required}")
    @Size(max = 120)
    private String firstName;

    @NotBlank(message = "{message.the.last.name.is.required}")
    @Size(max = 120)
    private String lastName;

    @Email(message = "{message.invalid.email}")
    @Size(max = 200)
    private String email;

    private boolean enabled = true;

    private boolean mustChangePassword;

    @Size(max = 100)
    private String password;

    private Set<Long> roleIds = new LinkedHashSet<>();

    public static UserForm from(AppUser user) {
        UserForm f = new UserForm();
        f.id = user.getId();
        f.username = user.getUsername();
        f.firstName = user.getFirstName();
        f.lastName = user.getLastName();
        f.email = user.getEmail();
        f.enabled = user.isEnabled();
        f.mustChangePassword = user.isMustChangePassword();
        user.getRoles().forEach(r -> f.roleIds.add(r.getId()));
        return f;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public void setMustChangePassword(boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Set<Long> getRoleIds() {
        return roleIds;
    }

    public void setRoleIds(Set<Long> roleIds) {
        this.roleIds = roleIds == null ? new LinkedHashSet<>() : roleIds;
    }
}
