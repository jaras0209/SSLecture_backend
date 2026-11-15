package com.sslecture.church;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChurchRepository extends JpaRepository<Church, UUID> {

  Optional<Church> findByName(String name);
}
