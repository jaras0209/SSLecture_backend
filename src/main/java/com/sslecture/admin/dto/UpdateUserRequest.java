package com.sslecture.admin.dto;

/**
 * {@code PUT /admin/users/:username}：欄位均選填，沒有送的不改。{@code gender} 送空字串表示清除。
 *
 * <p>{@code password} 為管理員設定的臨時密碼（前端每次隨機產生），對方下次登入須先更換。
 */
public record UpdateUserRequest(
    String displayName, String role, String church, String gender, String password) {}
