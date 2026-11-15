package com.sslecture.user;

import com.sslecture.common.jpa.CodedEnum;
import com.sslecture.common.jpa.CodedEnumConverter;
import jakarta.persistence.Converter;

/** 性別，只用於 student、teacher（見 database_schema.md 5.10）。 */
public enum Gender implements CodedEnum {
  MALE("male"),
  FEMALE("female");

  private final String code;

  Gender(String code) {
    this.code = code;
  }

  @Override
  public String code() {
    return code;
  }

  @Converter(autoApply = true)
  public static class DbConverter extends CodedEnumConverter<Gender> {
    public DbConverter() {
      super(Gender.class);
    }
  }
}
