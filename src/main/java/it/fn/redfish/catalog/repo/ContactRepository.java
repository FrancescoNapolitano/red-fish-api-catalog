package it.fn.redfish.catalog.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import it.fn.redfish.catalog.domain.Contact;

public interface ContactRepository extends JpaRepository<Contact, Long> {

    List<Contact> findByGroupIdOrderByNameAsc(Long groupId);

    List<Contact> findByServiceIdOrderByNameAsc(Long serviceId);
}
