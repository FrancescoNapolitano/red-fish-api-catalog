package it.fn.redfish.catalog.web.form;

import it.fn.redfish.catalog.domain.ApiGroup;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class GroupForm {

    private Long id;

    @NotBlank(message = "{message.the.name.is.required}")
    @Size(max = 160)
    private String name;

    @Size(max = 180)
    private String slug;

    private Long parentId;

    private String description;

    private String documentation;

    private int sortOrder;

    public static GroupForm from(ApiGroup group) {
        GroupForm f = new GroupForm();
        f.id = group.getId();
        f.name = group.getName();
        f.slug = group.getSlug();
        f.parentId = group.getParent() == null ? null : group.getParent().getId();
        f.description = group.getDescription();
        f.documentation = group.getDocumentation();
        f.sortOrder = group.getSortOrder();
        return f;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDocumentation() {
        return documentation;
    }

    public void setDocumentation(String documentation) {
        this.documentation = documentation;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
