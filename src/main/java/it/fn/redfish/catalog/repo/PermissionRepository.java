package it.fn.redfish.catalog.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import it.fn.redfish.catalog.domain.Permission;

public interface PermissionRepository extends JpaRepository<Permission, String> {

    List<Permission> findAllByOrderBySortOrderAsc();

    List<Permission> findByGroupScopedTrueOrderBySortOrderAsc();
}
