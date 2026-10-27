package com.happymouse.cryd.repository;

import com.happymouse.cryd.model.entity.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SystemConfigRepository extends JpaRepository<SystemConfig, Long> {
    Optional<SystemConfig> findByConfigKey(String configKey);
    List<SystemConfig> findByCategory(String category);
}
