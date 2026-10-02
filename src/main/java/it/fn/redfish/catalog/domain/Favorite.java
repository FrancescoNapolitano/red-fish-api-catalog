package it.fn.redfish.catalog.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "favorite")
public class Favorite {

    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "user_id")
        private Long userId;

        @Column(name = "service_id")
        private Long serviceId;

        protected Key() {
        }

        public Key(Long userId, Long serviceId) {
            this.userId = userId;
            this.serviceId = serviceId;
        }

        public Long getUserId() {
            return userId;
        }

        public Long getServiceId() {
            return serviceId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key other)) {
                return false;
            }
            return Objects.equals(userId, other.userId) && Objects.equals(serviceId, other.serviceId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, serviceId);
        }
    }

    @EmbeddedId
    private Key key;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private AppUser user;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @MapsId("serviceId")
    @JoinColumn(name = "service_id")
    private ApiService service;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Favorite() {
    }

    public Favorite(AppUser user, ApiService service) {
        this.user = user;
        this.service = service;
        this.key = new Key(user.getId(), service.getId());
    }

    public Key getKey() {
        return key;
    }

    public AppUser getUser() {
        return user;
    }

    public ApiService getService() {
        return service;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
