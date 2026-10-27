package com.happymouse.cryd.repository;

import com.happymouse.cryd.model.entity.ResourceFavorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResourceFavoriteRepository extends JpaRepository<ResourceFavorite, Long> {
    List<ResourceFavorite> findByUserId(Long userId);
    Optional<ResourceFavorite> findByResourceIdAndUserId(Long resourceId, Long userId);
    long countByResourceId(Long resourceId);
}
