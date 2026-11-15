package com.sslecture.common.jpa;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 資料庫與 API 都以小寫字串表示的列舉（例如 {@code student}、{@code google}）。
 *
 * <p>Java 列舉常數維持大寫，存取時一律經過 {@link #code()}。
 */
public interface CodedEnum {

  @JsonValue
  String code();

  /** 由小寫代碼找回列舉值；找不到時拋出 {@link IllegalArgumentException}。 */
  static <E extends Enum<E> & CodedEnum> E fromCode(Class<E> type, String code) {
    for (E value : type.getEnumConstants()) {
      if (value.code().equals(code)) {
        return value;
      }
    }
    throw new IllegalArgumentException("Unknown " + type.getSimpleName() + " code: " + code);
  }
}
