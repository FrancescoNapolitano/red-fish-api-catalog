package it.fn.redfish.catalog.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import it.fn.redfish.catalog.domain.Favorite;

public interface FavoriteRepository extends JpaRepository<Favorite, Favorite.Key> {

    @Query("select f from Favorite f join fetch f.service s join fetch s.group where f.user.id = :userId order by s.name")
    List<Favorite> findByUser(Long userId);

    @Query("select f.service.id from Favorite f where f.user.id = :userId")
    List<Long> findServiceIdsByUser(Long userId);
}
