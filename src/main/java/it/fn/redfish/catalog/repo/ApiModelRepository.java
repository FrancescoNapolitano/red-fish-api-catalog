package it.fn.redfish.catalog.repo;

import java.util.List;
import it.fn.redfish.catalog.domain.ServiceStatus;
import it.fn.redfish.catalog.domain.ServiceType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import it.fn.redfish.catalog.domain.ApiModel;

public interface ApiModelRepository extends JpaRepository<ApiModel, Long> {

    List<ApiModel> findBySpecVersionIdOrderBySortOrderAscNameAsc(Long specVersionId);

    void deleteBySpecVersionId(Long specVersionId);

    @Query("""
            select m from ApiModel m
            join fetch m.service
            join fetch m.specVersion
            where m.id = :id
            """)
    Optional<ApiModel> findByIdWithRelations(Long id);

    @Query("""
            select m from ApiModel m
            join fetch m.service s
            join fetch s.group
            where m.specVersion.current = true
              and (lower(m.name) like lower(concat('%', :q, '%'))
                or lower(coalesce(m.description, '')) like lower(concat('%', :q, '%')))
              and (:groupPath is null or s.group.path = :groupPath or s.group.path like concat(:groupPath, '/%'))
              and (:type is null or s.serviceType = :type)
              and (:status is null or s.status = :status)
            order by m.name, m.id
            """)
    Slice<ApiModel> search(String q, String groupPath, ServiceType type, ServiceStatus status, Pageable pageable);
}
