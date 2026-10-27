package com.happymouse.cryd.repository;

import com.happymouse.cryd.model.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CourseRepository extends JpaRepository<Course, Long> {
    List<Course> findByClassName(String className);
    List<Course> findAllByName(String name);
}
