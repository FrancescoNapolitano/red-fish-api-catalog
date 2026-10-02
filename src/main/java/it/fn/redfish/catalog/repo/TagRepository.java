package it.fn.redfish.catalog.repo;

import it.fn.redfish.catalog.domain.ServiceType;
import it.fn.redfish.catalog.domain.ServiceStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import it.fn.redfish.catalog.domain.Tag;

public interface TagRepository extends JpaRepository<Tag, Long> {

    Optional<Tag> findByNameIgnoreCase(String name);

    List<Tag> findAllByOrderByNameAsc();

    @Query("""
            select t from Tag t where lower(t.name) like lower(concat('%', :q, '%'))
              and ((:groupPath is null and :type is null and :status is null) or exists (select s.id from ApiService s join s.tags st where st.id = t.id
                  and (:groupPath is null or s.group.path = :groupPath or s.group.path like concat(:groupPath, '/%'))
                  and (:type is null or s.serviceType = :type)
                  and (:status is null or s.status = :status)))
            order by t.name, t.id
            """)
    Slice<Tag> search(String q, String groupPath,
            ServiceType type, ServiceStatus status,
            Pageable pageable);
}
