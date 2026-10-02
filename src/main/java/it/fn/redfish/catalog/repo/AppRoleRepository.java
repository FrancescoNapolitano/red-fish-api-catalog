package it.fn.redfish.catalog.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import it.fn.redfish.catalog.domain.AppRole;

public interface AppRoleRepository extends JpaRepository<AppRole, Long> {

    Optional<AppRole> findByNameIgnoreCase(String name);

    List<AppRole> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);
}
