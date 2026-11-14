package com.sslecture.common.time;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 牆上時間（使用者選的時間）轉換，見 database_schema.md 5.14。
 *
 * <p>前端送來的聽課、預約時間是 {@code YYYY-MM-DDTHH:mm}、不帶時區，代表台灣時間。後端以 {@code Asia/Taipei}
 * 解讀後存成帶時區的時間，回傳時再轉回同樣格式。不可把它當成 UTC 或依伺服器時區解讀。
 */
public final class WallClockTime {

  public static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");

  private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

  private WallClockTime() {}

  /**
   * 解析前端送來的牆上時間。
   *
   * @throws DateTimeParseException 格式不是 {@code YYYY-MM-DDTHH:mm}
   */
  public static OffsetDateTime parse(String value) {
    return LocalDateTime.parse(value, FORMAT).atZone(TAIPEI).toOffsetDateTime();
  }

  /** 轉回前端使用的 {@code YYYY-MM-DDTHH:mm}（台灣時間）。 */
  public static String format(OffsetDateTime value) {
    return value.atZoneSameInstant(TAIPEI).toLocalDateTime().format(FORMAT);
  }

  /** 以 UTC 表示同一時刻，方便與系統時間戳記比較。 */
  public static OffsetDateTime toUtc(OffsetDateTime value) {
    return value.withOffsetSameInstant(ZoneOffset.UTC);
  }
}
