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
@Table(name = "external_link")
public class ExternalLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private ApiService service;

    @Column(name = "label", length = 120, nullable = false)
    private String label;

    @Column(name = "url", length = 1000, nullable = false)
    private String url;

    @Column(name = "kind", length = 40)
    private String kind;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected ExternalLink() {
    }

    public ExternalLink(ApiService service, String label, String url, String kind) {
        this.service = service;
        this.label = label;
        this.url = url;
        this.kind = kind;
    }

    public Long getId() {
        return id;
    }

    public ApiService getService() {
        return service;
    }

    public void setService(ApiService service) {
        this.service = service;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getIcon() {
        if (kind == null) {
            return "bi-link-45deg";
        }
        return switch (kind.toUpperCase()) {
            case "GIT", "REPOSITORY" -> "bi-git";
            case "WIKI", "CONFLUENCE" -> "bi-journal-text";
            case "JIRA", "ISSUE" -> "bi-kanban";
            case "ENV", "AMBIENTE" -> "bi-hdd-network";
            case "MONITORING" -> "bi-activity";
            default -> "bi-link-45deg";
        };
    }
}
