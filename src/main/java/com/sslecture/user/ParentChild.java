package com.sslecture.user;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/** 家長綁定的孩子（database_schema.md 2.19）。 */
@Entity
@Table(name = "parent_children")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParentChild {

  @EmbeddedId private Key id;

  @CreationTimestamp
  @Column(name = "bound_at", nullable = false, updatable = false)
  private OffsetDateTime boundAt;

  public ParentChild(UUID parentId, UUID studentId) {
    this.id = new Key(parentId, studentId);
  }

  @Embeddable
  public record Key(
      @Column(name = "parent_id", nullable = false) UUID parentId,
      @Column(name = "student_id", nullable = false) UUID studentId)
      implements Serializable {}
}
