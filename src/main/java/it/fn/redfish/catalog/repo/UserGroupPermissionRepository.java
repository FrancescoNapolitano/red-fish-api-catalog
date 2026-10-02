package it.fn.redfish.catalog.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import it.fn.redfish.catalog.domain.UserGroupPermission;

public interface UserGroupPermissionRepository extends JpaRepository<UserGroupPermission, Long> {

    @Query("select p from UserGroupPermission p join fetch p.group where p.user.id = :userId order by p.group.path, p.permissionCode")
    List<UserGroupPermission> findByUser(Long userId);

    @Query("""
            select count(p) from UserGroupPermission p
            where p.user.id = :userId
              and p.permissionCode = :code
              and (p.group.path = :path or :path like concat(p.group.path, '/%'))
            """)
    long countGrant(Long userId, String code, String path);

    @Query("select p.group.path from UserGroupPermission p where p.user.id = :userId and p.permissionCode = :code")
    List<String> findGrantedPaths(Long userId, String code);

    void deleteByUserId(Long userId);

    void deleteByGroupId(Long groupId);
}
