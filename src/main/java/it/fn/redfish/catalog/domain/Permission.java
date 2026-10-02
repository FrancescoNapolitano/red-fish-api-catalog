package it.fn.redfish.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "permission")
public class Permission {

    @Id
    @Column(name = "code", length = 60, nullable = false)
    private String code;

    @Column(name = "label", length = 160, nullable = false)
    private String label;

    @Column(name = "category", length = 60, nullable = false)
    private String category;

    @Column(name = "group_scoped", nullable = false)
    private boolean groupScoped;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected Permission() {
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return it.fn.redfish.catalog.support.I18n.text("permission." + code);
    }

    public String getCategory() {
        return it.fn.redfish.catalog.support.I18n.text("permission.category." + category);
    }

    public boolean isGroupScoped() {
        return groupScoped;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
