package it.fn.redfish.catalog.repo;

import java.util.Collection;
import java.util.List;
import it.fn.redfish.catalog.domain.ServiceStatus;
import it.fn.redfish.catalog.domain.ServiceType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import it.fn.redfish.catalog.domain.ApiGroup;

public interface ApiGroupRepository extends JpaRepository<ApiGroup, Long> {

    List<ApiGroup> findByParentIsNullOrderBySortOrderAscNameAsc();

    List<ApiGroup> findByParentIdOrderBySortOrderAscNameAsc(Long parentId);

    Optional<ApiGroup> findByPath(String path);

    List<ApiGroup> findAllByOrderByPathAsc();

    List<ApiGroup> findByPathIn(Collection<String> paths);

    @Query("select g from ApiGroup g where g.path = :path or g.path like concat(:path, '/%') order by g.path")
    List<ApiGroup> findSubtree(String path);

    @Query("""
            select g from ApiGroup g
            where (lower(g.name) like lower(concat('%', :q, '%'))
               or lower(coalesce(g.description, '')) like lower(concat('%', :q, '%'))
               or lower(g.path) like lower(concat('%', :q, '%'))
              )
              and (:groupPath is null or g.path = :groupPath or g.path like concat(:groupPath, '/%'))
              and ((:type is null and :status is null) or exists
                  (select s.id from ApiService s where (s.group.path = g.path or s.group.path like concat(g.path, '/%'))
                   and (:type is null or s.serviceType = :type) and (:status is null or s.status = :status)))
            order by g.path, g.id
            """)
    Slice<ApiGroup> search(String q, String groupPath, ServiceType type, ServiceStatus status, Pageable pageable);

    @Query("select count(g) from ApiGroup g where g.parent.id = :parentId and lower(g.slug) = lower(:slug) and (:excludeId is null or g.id <> :excludeId)")
    long countSiblingSlug(Long parentId, String slug, Long excludeId);

    @Query("select count(g) from ApiGroup g where g.parent is null and lower(g.slug) = lower(:slug) and (:excludeId is null or g.id <> :excludeId)")
    long countRootSlug(String slug, Long excludeId);
}
