package com.sslecture.common.api;

/**
 * 標準回應格式（見 backend_api_spec.md「標準回應格式」）。
 *
 * <pre>
 * 成功：{ "success": true,  "data": { ... } }
 * 失敗：{ "success": false, "error": { "code": "UNAUTHORIZED", "message": "請先登入" } }
 * </pre>
 *
 * @param <T> 資料型別
 */
public record ApiResponse<T>(boolean success, T data, ApiError error) {

  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>(true, data, null);
  }

  public static ApiResponse<Void> ok() {
    return new ApiResponse<>(true, null, null);
  }

  public static ApiResponse<Void> fail(ErrorCode code) {
    return fail(code, code.defaultMessage());
  }

  public static ApiResponse<Void> fail(ErrorCode code, String message) {
    return new ApiResponse<>(false, null, new ApiError(code.name(), message));
  }

  /**
   * 錯誤內容。
   *
   * @param code 錯誤碼，前端依此顯示翻譯後的訊息
   * @param message 預設訊息
   */
  public record ApiError(String code, String message) {}
}
