package it.fn.redfish.catalog.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import it.fn.redfish.catalog.domain.AppSetting;

public interface AppSettingRepository extends JpaRepository<AppSetting, String> {
}
