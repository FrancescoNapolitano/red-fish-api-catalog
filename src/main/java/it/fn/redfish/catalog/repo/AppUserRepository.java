package it.fn.redfish.catalog.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import it.fn.redfish.catalog.domain.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    @Query("select u from AppUser u left join fetch u.roles where lower(u.username) = lower(:username)")
    Optional<AppUser> findByUsernameFetchRoles(String username);

    Optional<AppUser> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    List<AppUser> findAllByOrderByUsernameAsc();

    long countByEnabledTrue();

    @Query("select count(u) from AppUser u join u.roles r where lower(r.name) = 'admin' and u.enabled = true")
    long countEnabledAdmins();
}
