package com.sslecture.user;

import com.sslecture.common.jpa.CodedEnum;
import com.sslecture.common.jpa.CodedEnumConverter;
import jakarta.persistence.Converter;

/** 角色，決定整個系統的存取權限。 */
public enum Role implements CodedEnum {
  STUDENT("student"),
  TEACHER("teacher"),
  PASTOR("pastor"),
  PARENT("parent"),
  ADMIN("admin");

  private final String code;

  Role(String code) {
    this.code = code;
  }

  @Override
  public String code() {
    return code;
  }

  @Converter(autoApply = true)
  public static class DbConverter extends CodedEnumConverter<Role> {
    public DbConverter() {
      super(Role.class);
    }
  }
}
