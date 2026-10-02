package it.fn.redfish.catalog.repo;

import java.util.List;
import it.fn.redfish.catalog.domain.ServiceType;
import org.springframework.data.domain.Slice;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import it.fn.redfish.catalog.domain.ApiService;
import it.fn.redfish.catalog.domain.ServiceStatus;

public interface ApiServiceRepository extends JpaRepository<ApiService, Long> {

    List<ApiService> findByGroupIdOrderByNameAsc(Long groupId);

    Optional<ApiService> findByGroupIdAndSlugIgnoreCase(Long groupId, String slug);

    long countByGroupId(Long groupId);

    @Query("select s from ApiService s where s.group.path = :path or s.group.path like concat(:path, '/%') order by s.name")
    List<ApiService> findInSubtree(String path);

    @Query("select count(s) from ApiService s where s.group.path = :path or s.group.path like concat(:path, '/%')")
    long countInSubtree(String path);

    long countByStatus(ServiceStatus status);

    @Query("select s from ApiService s order by s.updatedAt desc")
    List<ApiService> findRecentlyUpdated(Pageable pageable);

    @Query("""
            select distinct s from ApiService s
            left join s.tags t
            where (lower(s.name) like lower(concat('%', :q, '%'))
               or lower(coalesce(s.description, '')) like lower(concat('%', :q, '%'))
               or lower(coalesce(s.version, '')) like lower(concat('%', :q, '%'))
               or lower(t.name) like lower(concat('%', :q, '%'))
              )
              and (:groupPath is null or s.group.path = :groupPath or s.group.path like concat(:groupPath, '/%'))
              and (:type is null or s.serviceType = :type)
              and (:status is null or s.status = :status)
            order by s.name, s.id
            """)
    Slice<ApiService> search(String q, String groupPath, ServiceType type, ServiceStatus status, Pageable pageable);

    @Query("select count(s) from ApiService s where lower(s.slug) = lower(:slug) and s.group.id = :groupId and (:excludeId is null or s.id <> :excludeId)")
    long countSlugInGroup(Long groupId, String slug, Long excludeId);

    @Query("select distinct s from ApiService s join s.tags t where t.id = :tagId order by s.name")
    List<ApiService> findByTag(Long tagId);

    @Query("select s.group.id as groupId, count(s) as total from ApiService s group by s.group.id")
    List<GroupCount> countGroupedByGroup();

    @Query("select s from ApiService s where not exists (select c.id from Contact c where c.service = s) order by s.name, s.id")
    List<ApiService> findWithoutContacts(Pageable pageable);

    @Query("select s from ApiService s where not exists (select e.id from ServiceEnvironment e where e.service = s and e.baseUrl is not null and trim(e.baseUrl) <> '') order by s.name, s.id")
    List<ApiService> findWithoutEnvironmentUrl(Pageable pageable);

    interface GroupCount {
        Long getGroupId();

        long getTotal();
    }
}
