package com.sslecture.church;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 教會（database_schema.md 2.2）。目前只供讀取，由 Liquibase 種子資料建立。 */
@Entity
@Table(name = "churches")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Church {

  @Id private UUID id;

  @Column(nullable = false, unique = true, length = 100)
  private String name;

  @Column(length = 100)
  private String region;

  @Column(name = "is_active", nullable = false)
  private boolean active;

  @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
  private OffsetDateTime createdAt;
}
