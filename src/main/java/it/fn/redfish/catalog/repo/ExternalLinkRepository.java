package it.fn.redfish.catalog.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import it.fn.redfish.catalog.domain.ExternalLink;

public interface ExternalLinkRepository extends JpaRepository<ExternalLink, Long> {

    List<ExternalLink> findByServiceIdOrderBySortOrderAscLabelAsc(Long serviceId);
}
