package com.sslecture.auth.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * {@code POST /auth/register}。
 *
 * @param church 教會名稱；沒有邀請碼或邀請碼未指定教會時必填
 * @param childUsernames 只在邀請碼為 parent 時使用，須至少一位同教會的學員
 */
public record RegisterRequest(
    @NotBlank(message = "必填") String username,
    String email,
    @NotBlank(message = "必填") String password,
    String church,
    String inviteCode,
    List<String> childUsernames) {}
