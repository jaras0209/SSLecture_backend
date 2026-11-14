package com.sslecture.common.api;

import java.time.Duration;

/** 業務規則錯誤，由 {@link GlobalExceptionHandler} 轉成標準錯誤回應。 */
public class BusinessException extends RuntimeException {

  private final ErrorCode code;
  private final Duration retryAfter;

  public BusinessException(ErrorCode code) {
    this(code, code.defaultMessage(), null);
  }

  public BusinessException(ErrorCode code, String message) {
    this(code, message, null);
  }

  private BusinessException(ErrorCode code, String message, Duration retryAfter) {
    super(message);
    this.code = code;
    this.retryAfter = retryAfter;
  }

  /** 嘗試次數過多（429），並告知前端還要等多久（Retry-After 標頭）。 */
  public static BusinessException tooManyRequests(Duration retryAfter) {
    return new BusinessException(
        ErrorCode.TOO_MANY_REQUESTS, ErrorCode.TOO_MANY_REQUESTS.defaultMessage(), retryAfter);
  }

  public ErrorCode code() {
    return code;
  }

  /** 需等待的時間；不是 429 時為 {@code null}。 */
  public Duration retryAfter() {
    return retryAfter;
  }
}
