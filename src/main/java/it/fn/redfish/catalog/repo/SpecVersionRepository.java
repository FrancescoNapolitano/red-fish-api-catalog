package it.fn.redfish.catalog.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import it.fn.redfish.catalog.domain.SpecVersion;

public interface SpecVersionRepository extends JpaRepository<SpecVersion, Long> {

    List<SpecVersion> findByServiceIdOrderByRevisionDesc(Long serviceId);

    Optional<SpecVersion> findByServiceIdAndCurrentTrue(Long serviceId);

    Optional<SpecVersion> findByServiceIdAndRevision(Long serviceId, int revision);

    @Query("select coalesce(max(v.revision), 0) from SpecVersion v where v.service.id = :serviceId")
    int maxRevision(Long serviceId);

    @Query("select v from SpecVersion v join fetch v.service s join fetch s.group order by v.importedAt desc")
    List<SpecVersion> findRecentImports(Pageable pageable);
}
