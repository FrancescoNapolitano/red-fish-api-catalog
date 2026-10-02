package it.fn.redfish.catalog.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import it.fn.redfish.catalog.domain.HealthStatus;
import it.fn.redfish.catalog.domain.ServiceEnvironment;

public interface ServiceEnvironmentRepository extends JpaRepository<ServiceEnvironment, Long> {

    List<ServiceEnvironment> findByServiceIdOrderBySortOrderAscNameAsc(Long serviceId);

    Optional<ServiceEnvironment> findByServiceIdAndNameIgnoreCase(Long serviceId, String name);

    @Query("select e from ServiceEnvironment e join fetch e.service where e.healthCheckUrl is not null and e.healthCheckUrl <> ''")
    List<ServiceEnvironment> findMonitored();

    @Query("""
            select e from ServiceEnvironment e join fetch e.service
            where e.healthCheckUrl is not null and e.healthCheckUrl <> ''
              and e.healthStatus = :status
            """)
    List<ServiceEnvironment> findMonitoredByHealthStatus(HealthStatus status);
}
