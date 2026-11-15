package com.sslecture.auth.attempt;

import com.sslecture.common.jpa.CodedEnum;
import com.sslecture.common.jpa.CodedEnumConverter;
import jakarta.persistence.Converter;

/** 會被拿來猜的動作（database_schema.md 2.22）。 */
public enum AuthAction implements CodedEnum {
  LOGIN("login"),
  INVITE_CODE("invite_code"),
  PASSWORD_FORGOT("password_forgot"),
  PASSWORD_RESET("password_reset"),
  PASSWORD_CHANGE("password_change"),
  OAUTH_EXCHANGE("oauth_exchange");

  private final String code;

  AuthAction(String code) {
    this.code = code;
  }

  @Override
  public String code() {
    return code;
  }

  @Converter(autoApply = true)
  public static class DbConverter extends CodedEnumConverter<AuthAction> {
    public DbConverter() {
      super(AuthAction.class);
    }
  }
}
