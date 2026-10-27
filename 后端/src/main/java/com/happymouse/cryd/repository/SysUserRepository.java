package com.happymouse.cryd.repository;

import com.happymouse.cryd.model.entity.SysUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SysUserRepository extends JpaRepository<SysUser, Long> {
    Optional<SysUser> findByUsername(String username);
    Optional<SysUser> findByPhone(String phone);
    Optional<SysUser> findByStudentId(String studentId);
    List<SysUser> findByRole(String role);
    List<SysUser> findByClassName(String className);
}
