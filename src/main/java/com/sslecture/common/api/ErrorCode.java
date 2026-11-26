package com.sslecture.common.api;

import org.springframework.http.HttpStatus;

/**
 * 錯誤碼（見 backend_api_spec.md 第 12 節）。
 *
 * <p>前端依錯誤碼顯示翻譯後的訊息；新增錯誤碼時須同步更新前端的錯誤碼表與 i18n。
 */
public enum ErrorCode {
  VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "請求參數格式錯誤或驗證失敗"),
  TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "嘗試次數過多，請稍後再試"),
  INVALID_DATE_FORMAT(HttpStatus.BAD_REQUEST, "日期時間格式不正確"),
  INVALID_STATUS_TRANSITION(HttpStatus.BAD_REQUEST, "場次狀態流轉不合法"),
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "請先登入"),
  INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "帳號或密碼錯誤"),
  USE_SOCIAL_LOGIN(HttpStatus.UNAUTHORIZED, "此帳號請使用 Google / LINE 登入"),
  TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Access Token 已過期"),
  REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "登入已過期，請重新登入"),
  FORBIDDEN(HttpStatus.FORBIDDEN, "沒有權限執行此操作"),
  STUDENT_NOT_VISIBLE(HttpStatus.FORBIDDEN, "無法存取此學員"),
  BOOKING_NOT_CONFIRMED(HttpStatus.CONFLICT, "場次時間尚未確認"),
  LECTURER_EXISTS(HttpStatus.CONFLICT, "這間教會已有同名、同稱謂的講師"),
  LECTURER_ACCOUNT_LINKED(HttpStatus.CONFLICT, "這個帳號已連結到另一筆講師資料"),
  ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "帳號已被停用"),
  USER_NOT_FOUND(HttpStatus.NOT_FOUND, "找不到此使用者"),
  COURSE_NOT_FOUND(HttpStatus.NOT_FOUND, "找不到此課程"),
  SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "找不到此聽課紀錄"),
  BOOKING_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "找不到此預約場次"),
  BOOKING_ATTENDEE_NOT_FOUND(HttpStatus.NOT_FOUND, "找不到此場次的學員記錄"),
  LECTURER_NOT_FOUND(HttpStatus.NOT_FOUND, "找不到此講師"),
  STATS_NOT_FOUND(HttpStatus.NOT_FOUND, "找不到此年度教學統計"),
  USERNAME_TAKEN(HttpStatus.CONFLICT, "帳號名稱已被使用"),
  EMAIL_TAKEN(HttpStatus.CONFLICT, "信箱已被其他帳號使用"),
  THEME_EXISTS(HttpStatus.CONFLICT, "同一教會已有同名的專題主題"),
  WEAK_PASSWORD(HttpStatus.BAD_REQUEST, "密碼不符合密碼規則"),
  PASSWORD_CHANGE_REQUIRED(HttpStatus.FORBIDDEN, "請先修改密碼"),
  ATTENDEE_ALREADY_EXISTS(HttpStatus.CONFLICT, "該學員已加入此場次"),
  CHURCH_REQUIRED(HttpStatus.BAD_REQUEST, "請選擇所屬教會"),
  INVALID_INVITE_CODE(HttpStatus.BAD_REQUEST, "邀請碼無效、過期或已作廢"),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "伺服器內部錯誤");

  private final HttpStatus status;
  private final String defaultMessage;

  ErrorCode(HttpStatus status, String defaultMessage) {
    this.status = status;
    this.defaultMessage = defaultMessage;
  }

  public HttpStatus status() {
    return status;
  }

  public String defaultMessage() {
    return defaultMessage;
  }
}
