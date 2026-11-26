package com.sslecture.auth.invite;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface InviteCodeRepository extends JpaRepository<InviteCode, String> {

  /** 鎖住這一列直到交易結束，避免兩個人同時用同一組邀請碼註冊。 */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from InviteCode c where c.code = :code")
  Optional<InviteCode> findForUpdate(String code);

  /** 全部邀請碼，新的在前（管理員頁面）。 */
  @EntityGraph(attributePaths = {"church", "createdBy", "usedBy"})
  @Query("select c from InviteCode c order by c.createdAt desc")
  List<InviteCode> findAllNewestFirst();
}
