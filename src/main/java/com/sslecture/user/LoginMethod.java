package com.sslecture.user;

import com.sslecture.common.jpa.CodedEnum;
import com.sslecture.common.jpa.CodedEnumConverter;
import jakarta.persistence.Converter;

/** 帳號建立時的登入方式；google、line 帳號沒有密碼。 */
public enum LoginMethod implements CodedEnum {
  CREDENTIALS("credentials"),
  GOOGLE("google"),
  LINE("line");

  private final String code;

  LoginMethod(String code) {
    this.code = code;
  }

  @Override
  public String code() {
    return code;
  }

  @Converter(autoApply = true)
  public static class DbConverter extends CodedEnumConverter<LoginMethod> {
    public DbConverter() {
      super(LoginMethod.class);
    }
  }
}
