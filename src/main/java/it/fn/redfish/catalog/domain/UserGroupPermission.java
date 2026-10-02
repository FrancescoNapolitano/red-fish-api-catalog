package it.fn.redfish.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_group_permission")
public class UserGroupPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private ApiGroup group;

    @Column(name = "permission_code", length = 60, nullable = false)
    private String permissionCode;

    protected UserGroupPermission() {
    }

    public UserGroupPermission(AppUser user, ApiGroup group, String permissionCode) {
        this.user = user;
        this.group = group;
        this.permissionCode = permissionCode;
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public ApiGroup getGroup() {
        return group;
    }

    public String getPermissionCode() {
        return permissionCode;
    }
}
