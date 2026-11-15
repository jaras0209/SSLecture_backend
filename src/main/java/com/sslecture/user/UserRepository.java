package com.sslecture.user;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
}
