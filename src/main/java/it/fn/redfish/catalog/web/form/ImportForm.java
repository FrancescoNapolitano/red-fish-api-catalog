package it.fn.redfish.catalog.web.form;

import org.springframework.web.multipart.MultipartFile;

import it.fn.redfish.catalog.domain.SourceType;

public class ImportForm {

    private Long groupId;

    private Long serviceId;

    private String serviceName;

    private SourceType sourceType = SourceType.UPLOAD;

    private MultipartFile file;

    private String url;

    private String tags;

    private String notes;

    private boolean makeCurrent = true;

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public Long getServiceId() {
        return serviceId;
    }

    public void setServiceId(Long serviceId) {
        this.serviceId = serviceId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(SourceType sourceType) {
        this.sourceType = sourceType;
    }

    public MultipartFile getFile() {
        return file;
    }

    public void setFile(MultipartFile file) {
        this.file = file;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public boolean isMakeCurrent() {
        return makeCurrent;
    }

    public void setMakeCurrent(boolean makeCurrent) {
        this.makeCurrent = makeCurrent;
    }
}
