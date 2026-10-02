package it.fn.redfish.catalog.repo;

import java.util.List;
import it.fn.redfish.catalog.domain.ServiceStatus;
import it.fn.redfish.catalog.domain.ServiceType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import it.fn.redfish.catalog.domain.ApiEndpoint;

public interface ApiEndpointRepository extends JpaRepository<ApiEndpoint, Long> {

    List<ApiEndpoint> findBySpecVersionIdOrderBySortOrderAsc(Long specVersionId);

    void deleteBySpecVersionId(Long specVersionId);

    @Query("select count(e) from ApiEndpoint e where e.specVersion.current = true")
    long countCurrent();

    @Query("""
            select e from ApiEndpoint e
            join fetch e.service s
            join fetch s.group
            where e.specVersion.current = true
              and (lower(e.path) like lower(concat('%', :q, '%'))
                or lower(coalesce(e.summary, '')) like lower(concat('%', :q, '%'))
                or lower(coalesce(e.description, '')) like lower(concat('%', :q, '%'))
                or lower(coalesce(e.operationId, '')) like lower(concat('%', :q, '%'))
                or lower(coalesce(e.tags, '')) like lower(concat('%', :q, '%')))
              and (:groupPath is null or s.group.path = :groupPath or s.group.path like concat(:groupPath, '/%'))
              and (:type is null or s.serviceType = :type)
              and (:status is null or s.status = :status)
            order by s.name, e.path, e.id
            """)
    Slice<ApiEndpoint> search(String q, String groupPath, ServiceType type, ServiceStatus status, Pageable pageable);
}
