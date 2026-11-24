package com.kganesh.ecommerce.profile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ChildProfileRepository extends JpaRepository<ChildProfile, UUID> {

    List<ChildProfile> findByUserIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(UUID userId);

    Optional<ChildProfile> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    long countByUserIdAndDeletedAtIsNull(UUID userId);
}
