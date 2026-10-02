package it.fn.redfish.catalog.web.form;

import java.util.stream.Collectors;

import it.fn.redfish.catalog.domain.ApiService;
import it.fn.redfish.catalog.domain.ServiceStatus;
import it.fn.redfish.catalog.domain.ServiceType;
import it.fn.redfish.catalog.domain.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class ServiceForm {

    private Long id;

    @NotNull(message = "{message.the.group.is.required}")
    private Long groupId;

    @NotBlank(message = "{message.the.name.is.required}")
    @Size(max = 200)
    private String name;

    @Size(max = 220)
    private String slug;

    private String description;

    @Size(max = 60)
    private String version;

    @NotNull
    private ServiceType serviceType = ServiceType.REST;

    @NotNull
    private ServiceStatus status = ServiceStatus.ACTIVE;

    private String tags;

    public static ServiceForm from(ApiService service) {
        ServiceForm f = new ServiceForm();
        f.id = service.getId();
        f.groupId = service.getGroup().getId();
        f.name = service.getName();
        f.slug = service.getSlug();
        f.description = service.getDescription();
        f.version = service.getVersion();
        f.serviceType = service.getServiceType();
        f.status = service.getStatus();
        f.tags = service.getTags().stream().map(Tag::getName).collect(Collectors.joining(", "));
        return f;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public ServiceType getServiceType() {
        return serviceType;
    }

    public void setServiceType(ServiceType serviceType) {
        this.serviceType = serviceType;
    }

    public ServiceStatus getStatus() {
        return status;
    }

    public void setStatus(ServiceStatus status) {
        this.status = status;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }
}
