package it.fn.redfish.catalog.domain;

import java.time.Instant;

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
@Table(name = "service_comment")
public class ServiceComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private ApiService service;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "endpoint_id")
    private ApiEndpoint endpoint;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private AppUser author;

    @Column(name = "author_label", length = 160)
    private String authorLabel;

    @Column(name = "body", columnDefinition = "text", nullable = false)
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected ServiceComment() {
    }

    public ServiceComment(ApiService service, ApiEndpoint endpoint, AppUser author, String body) {
        this.service = service;
        this.endpoint = endpoint;
        this.author = author;
        this.authorLabel = author == null ? null : author.getDisplayName();
        this.body = body;
    }

    public Long getId() {
        return id;
    }

    public ApiService getService() {
        return service;
    }

    public ApiEndpoint getEndpoint() {
        return endpoint;
    }

    public AppUser getAuthor() {
        return author;
    }

    public String getAuthorLabel() {
        return authorLabel;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
