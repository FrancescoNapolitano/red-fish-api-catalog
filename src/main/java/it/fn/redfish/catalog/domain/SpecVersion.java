package it.fn.redfish.catalog.domain;

import it.fn.redfish.catalog.support.I18n;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "spec_version")
public class SpecVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private ApiService service;

    @Column(name = "revision", nullable = false)
    private int revision;

    @Column(name = "version_label", length = 80)
    private String versionLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "spec_format", length = 20, nullable = false)
    private SpecFormat specFormat;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", length = 20, nullable = false)
    private SourceType sourceType;

    @Column(name = "source_url", length = 2000)
    private String sourceUrl;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "content_type", length = 120)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "checksum", length = 64)
    private String checksum;

    @Column(name = "raw_content", columnDefinition = "text", nullable = false)
    private String rawContent;

    @Column(name = "title", length = 300)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "is_current", nullable = false)
    private boolean current;

    @Column(name = "endpoint_count", nullable = false)
    private int endpointCount;

    @Column(name = "model_count", nullable = false)
    private int modelCount;

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "imported_by")
    private AppUser importedBy;

    @Column(name = "imported_by_username", length = 80)
    private String importedByUsername;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    protected SpecVersion() {
    }

    public SpecVersion(ApiService service, int revision, SpecFormat specFormat, SourceType sourceType) {
        this.service = service;
        this.revision = revision;
        this.specFormat = specFormat;
        this.sourceType = sourceType;
    }

    public Long getId() {
        return id;
    }

    public ApiService getService() {
        return service;
    }

    public int getRevision() {
        return revision;
    }

    public String getVersionLabel() {
        return versionLabel;
    }

    public void setVersionLabel(String versionLabel) {
        this.versionLabel = versionLabel;
    }

    public SpecFormat getSpecFormat() {
        return specFormat;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public String getRawContent() {
        return rawContent;
    }

    public void setRawContent(String rawContent) {
        this.rawContent = rawContent;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isCurrent() {
        return current;
    }

    public void setCurrent(boolean current) {
        this.current = current;
    }

    public int getEndpointCount() {
        return endpointCount;
    }

    public void setEndpointCount(int endpointCount) {
        this.endpointCount = endpointCount;
    }

    public int getModelCount() {
        return modelCount;
    }

    public void setModelCount(int modelCount) {
        this.modelCount = modelCount;
    }

    public Instant getImportedAt() {
        return importedAt;
    }

    public void setImportedAt(Instant importedAt) {
        this.importedAt = importedAt;
    }

    public AppUser getImportedBy() {
        return importedBy;
    }

    public void setImportedBy(AppUser importedBy) {
        this.importedBy = importedBy;
    }

    public String getImportedByUsername() {
        return importedByUsername;
    }

    public void setImportedByUsername(String importedByUsername) {
        this.importedByUsername = importedByUsername;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getDisplayLabel() {
        String v = versionLabel == null || versionLabel.isBlank() ? I18n.text("message.n.a") : versionLabel;
        return "r" + revision + " · " + v;
    }

    public String downloadFileName() {
        if (fileName != null && !fileName.isBlank()) {
            return fileName;
        }
        return service.getSlug() + "-r" + revision + "." + specFormat.getDefaultExtension();
    }
}
