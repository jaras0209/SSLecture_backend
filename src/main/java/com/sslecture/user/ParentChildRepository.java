package com.sslecture.user;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ParentChildRepository extends JpaRepository<ParentChild, ParentChild.Key> {

  @Query(
      """
      select u.username from ParentChild pc join User u on u.id = pc.id.studentId
      where pc.id.parentId = :parentId order by u.username
      """)
  List<String> findChildUsernames(UUID parentId);
}
