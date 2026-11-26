package com.sslecture.admin;

import com.sslecture.admin.dto.CleanupCounts;
import com.sslecture.user.Role;
import com.sslecture.user.User;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 刪除帳號或改角色時，依「原本的角色」清掉仍指向它的資料（database_schema.md 5.12，前端
 * useAccountCleanup().releaseRole()）。在呼叫端的交易中執行。
 *
 * <p>已完成、已取消的預約、出席紀錄、教學統計與聽課紀錄保留為歷史。關懷配對、預約、講師這些表還沒有 JPA 實體，所以以 SQL 處理。
 */
@Component
class AccountCleanup {

  static final String REASON_DELETED = "輔導教師帳號已刪除";
  static final String REASON_ROLE_CHANGED = "輔導教師已不再擔任此身分";

  private final JdbcTemplate jdbc;
  private final Clock clock;

  AccountCleanup(JdbcTemplate jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  /**
   * 清理 {@code user} 以 {@code oldRole} 留下的關聯。
   *
   * @param newRole 改成的角色；刪除帳號時為 {@code null}。教師與牧者互換時保留講師連結（兩者都能授課）
   */
  CleanupCounts release(User user, Role oldRole, Role newRole) {
    String id = user.getId().toString();
    int mentorAssignments = 0;
    int lecturerLinks = 0;
    int cancelledBookings = 0;
    switch (oldRole) {
      case STUDENT -> {
        jdbc.update("DELETE FROM parent_children WHERE student_id = ?", id);
        mentorAssignments = jdbc.update("DELETE FROM student_assignments WHERE student_id = ?", id);
      }
      case TEACHER -> {
        mentorAssignments =
            jdbc.update(
                "UPDATE student_assignments SET teacher_id = NULL WHERE teacher_id = ?", id);
        cancelledBookings =
            jdbc.update(
                "UPDATE booking_sessions SET status = 'cancelled', cancel_reason = ?, updated_at ="
                    + " ? WHERE teacher_id = ? AND status IN ('pending', 'confirmed')",
                newRole == null ? REASON_DELETED : REASON_ROLE_CHANGED,
                Timestamp.from(Instant.now(clock)),
                id);
      }
      case PASTOR ->
          mentorAssignments =
              jdbc.update(
                  "UPDATE student_assignments SET pastor_id = NULL WHERE pastor_id = ?", id);
      case PARENT -> jdbc.update("DELETE FROM parent_children WHERE parent_id = ?", id);
      case ADMIN -> {}
    }
    boolean teaches = oldRole == Role.TEACHER || oldRole == Role.PASTOR;
    boolean stillTeaches = newRole == Role.TEACHER || newRole == Role.PASTOR;
    if (teaches && !stillTeaches) {
      lecturerLinks =
          jdbc.update("UPDATE lecturers SET linked_user_id = NULL WHERE linked_user_id = ?", id);
    }
    return new CleanupCounts(mentorAssignments, lecturerLinks, cancelledBookings);
  }
}
