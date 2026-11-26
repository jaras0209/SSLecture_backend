package com.sslecture.user;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, UUID> {

  /** 以帳號或信箱找使用者；兩者都以小寫儲存，呼叫端須先轉小寫。 */
  @EntityGraph(attributePaths = "church")
  @Query("select u from User u where u.username = :identifier or u.email = :identifier")
  Optional<User> findByIdentifier(String identifier);

  @EntityGraph(attributePaths = "church")
  Optional<User> findByUsername(String username);

  @EntityGraph(attributePaths = "church")
  Optional<User> findWithChurchById(UUID id);

  boolean existsByUsername(String username);

  boolean existsByEmail(String email);

  boolean existsByEmailAndIdNot(String email, UUID id);

  List<User> findByUsernameIn(Collection<String> usernames);

  /** 啟用中的帳號（管理員的帳號列表），可依角色、教會篩選；停用的帳號只保留名稱，不列出。 */
  @EntityGraph(attributePaths = "church")
  @Query(
      value =
          """
          select u from User u left join u.church c
          where u.active = true and (:role is null or u.role = :role)
            and (:church is null or c.name = :church)
          order by u.createdAt, u.username
          """,
      countQuery =
          """
          select count(u) from User u left join u.church c
          where u.active = true and (:role is null or u.role = :role)
            and (:church is null or c.name = :church)
          """)
  org.springframework.data.domain.Page<User> findActive(
      Role role, String church, Pageable pageable);
}
