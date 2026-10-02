package it.fn.redfish.catalog.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import it.fn.redfish.catalog.domain.ServiceComment;

public interface ServiceCommentRepository extends JpaRepository<ServiceComment, Long> {

    List<ServiceComment> findByServiceIdAndEndpointIsNullOrderByCreatedAtDesc(Long serviceId);

    List<ServiceComment> findByEndpointIdOrderByCreatedAtDesc(Long endpointId);

    long countByServiceId(Long serviceId);
}
