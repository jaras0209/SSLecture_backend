package com.sslecture.common.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import org.junit.jupiter.api.Test;

class WallClockTimeTests {

  @Test
  void parsesAsTaipeiTime() {
    OffsetDateTime parsed = WallClockTime.parse("2026-10-28T14:30");

    // 台灣 14:30 = UTC 06:30，不能被當成 UTC 14:30
    assertThat(WallClockTime.toUtc(parsed)).isEqualTo(OffsetDateTime.parse("2026-10-28T06:30:00Z"));
  }

  @Test
  void formatsBackToTaipeiWallClock() {
    assertThat(WallClockTime.format(OffsetDateTime.parse("2026-10-28T06:30:00Z")))
        .isEqualTo("2026-10-28T14:30");
  }

  @Test
  void rejectsValuesWithTimeZone() {
    assertThatThrownBy(() -> WallClockTime.parse("2026-10-28T14:30:00Z"))
        .isInstanceOf(DateTimeParseException.class);
  }
}
