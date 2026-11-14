package com.sslecture.common.api;

import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
