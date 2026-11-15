package com.sslecture.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 密碼規則須與前端 usePasswordStrength.ts 一致。 */
class AccountRulesTests {

  @ParameterizedTest
  @CsvSource({
    "abc12, false", // 未滿 6 字元
    "abcdef, false", // 0 項
    "abcdefgh, false", // 只有長度
    "abcde1, false", // 只有數字
    "abcdefg1, true", // 長度 + 數字
    "Abcdef1, true", // 大小寫 + 數字
    "abc!e1, true", // 數字 + 符號
    "密碼密碼密碼1, true", // 中文算符號 + 數字
  })
  void passwordNeedsTwoOfFourRules(String password, boolean expected) {
    assertThat(AccountRules.isStrongEnough(password)).isEqualTo(expected);
  }
}
