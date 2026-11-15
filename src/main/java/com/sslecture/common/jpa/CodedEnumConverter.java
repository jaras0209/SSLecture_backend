package com.sslecture.common.jpa;

import jakarta.persistence.AttributeConverter;

/** 把 {@link CodedEnum} 存成小寫代碼。各列舉建立一個子類別並加上 {@code @Converter(autoApply = true)}。 */
public abstract class CodedEnumConverter<E extends Enum<E> & CodedEnum>
    implements AttributeConverter<E, String> {

  private final Class<E> type;

  protected CodedEnumConverter(Class<E> type) {
    this.type = type;
  }

  @Override
  public String convertToDatabaseColumn(E attribute) {
    return attribute == null ? null : attribute.code();
  }

  @Override
  public E convertToEntityAttribute(String dbData) {
    return dbData == null ? null : CodedEnum.fromCode(type, dbData);
  }
}
