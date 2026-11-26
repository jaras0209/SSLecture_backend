package com.sslecture.common.api;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 把例外轉成標準錯誤回應 {@code { success: false, error: { code, message } }}。 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  /** MySQL 的「重複」錯誤：Duplicate entry '…' for key 'users.username' */
  private static final int MYSQL_DUPLICATE_ENTRY = 1062;

  private static final Pattern DUPLICATE_KEY = Pattern.compile("for key '(?:[\\w]+\\.)?([\\w]+)'");
  private static final Pattern CHECK_NAME = Pattern.compile("[Cc]heck constraint '([\\w]+)'");

  /** 唯一限制（前端 docs/database_schema.md 第 6 節的名稱）對應的錯誤碼。API 平常會先檢查，這裡處理兩個請求同時寫入、 都通過檢查後才被資料庫擋下的情況。 */
  private static final Map<String, ErrorCode> UNIQUE_KEYS =
      Map.of(
          "username", ErrorCode.USERNAME_TAKEN,
          "uq_users_email", ErrorCode.EMAIL_TAKEN,
          "uq_lecturers_church_name", ErrorCode.LECTURER_EXISTS,
          "uq_lecturers_linked_user", ErrorCode.LECTURER_ACCOUNT_LINKED,
          "uq_themes_church_type_name", ErrorCode.THEME_EXISTS);

  @ExceptionHandler(BusinessException.class)
  ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
    ResponseEntity.BodyBuilder builder = ResponseEntity.status(ex.code().status());
    if (ex.retryAfter() != null) {
      builder.header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.retryAfter().toSeconds()));
    }
    return builder.body(ApiResponse.fail(ex.code(), ex.getMessage()));
  }

  /** 前端直接顯示 VALIDATION_ERROR 的訊息，所以要指出是哪個欄位有問題。 */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + "：" + e.getDefaultMessage())
            .collect(Collectors.joining("；"));
    return error(ErrorCode.VALIDATION_ERROR, message);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
    return error(ErrorCode.VALIDATION_ERROR, "請求內容不是有效的 JSON");
  }

  /**
   * 資料庫擋下的寫入（唯一限制、CHECK、欄位長度、外鍵）。原本會成為 500；改為：已知的唯一限制回傳對應的 409，其餘回 400
   * VALIDATION_ERROR，並在訊息中指出是哪個限制（前端直接顯示 VALIDATION_ERROR 的訊息）。
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
    Optional<SQLException> sql = sqlCause(ex);
    String detail = sql.map(SQLException::getMessage).orElse("");
    if (sql.map(SQLException::getErrorCode).orElse(0) == MYSQL_DUPLICATE_ENTRY) {
      ErrorCode code = firstGroup(DUPLICATE_KEY, detail).map(UNIQUE_KEYS::get).orElse(null);
      if (code != null) {
        return error(code, code.defaultMessage());
      }
    }
    log.warn("Database refused a write: {}", detail);
    String rule =
        firstGroup(CHECK_NAME, detail).or(() -> firstGroup(DUPLICATE_KEY, detail)).orElse(null);
    return error(
        ErrorCode.VALIDATION_ERROR, rule == null ? "資料不符合資料庫的規則" : "資料不符合資料庫的規則（" + rule + "）");
  }

  private static Optional<SQLException> sqlCause(Throwable ex) {
    for (Throwable t = ex; t != null; t = t.getCause()) {
      if (t instanceof SQLException sql) {
        return Optional.of(sql);
      }
    }
    return Optional.empty();
  }

  private static Optional<String> firstGroup(Pattern pattern, String text) {
    Matcher m = pattern.matcher(text);
    return m.find() ? Optional.of(m.group(1)) : Optional.empty();
  }

  /** 找不到路徑時交回 Spring 預設處理（404），不當成伺服器錯誤。 */
  @ExceptionHandler(NoResourceFoundException.class)
  void handleNoResource(NoResourceFoundException ex) throws NoResourceFoundException {
    throw ex;
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
    log.error("Unhandled exception", ex);
    return error(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage());
  }

  private static ResponseEntity<ApiResponse<Void>> error(ErrorCode code, String message) {
    return ResponseEntity.status(code.status()).body(ApiResponse.fail(code, message));
  }
}
