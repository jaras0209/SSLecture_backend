package com.sslecture.admin.dto;

/** 刪除帳號或改角色時清理的筆數，前端據此提示管理員（前端 useAccountCleanup 的 ReleaseSummary）。 */
public record CleanupCounts(int mentorAssignments, int lecturerLinks, int cancelledBookings) {

  public static final CleanupCounts NONE = new CleanupCounts(0, 0, 0);
}
