package com.sslecture.admin.dto;

/** 刪除或更新帳號的結果：帳號與清理的筆數。 */
public record UserChange(AdminUser user, CleanupCounts cleanup) {}
