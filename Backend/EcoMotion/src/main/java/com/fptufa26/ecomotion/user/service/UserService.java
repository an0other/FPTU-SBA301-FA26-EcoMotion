package com.fptufa26.ecomotion.user.service;

import com.fptufa26.ecomotion.user.entity.Renter;
import com.fptufa26.ecomotion.user.entity.Role;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.entity.UserStatus;

import java.util.Optional;

public interface UserService {

    Optional<User> findByEmail(String email);

    User loadForAuthentication(String email);

    Optional<User> findById(Long userId);

    boolean existsByEmail(String email);

    User createUser(String email, String rawPassword, String fullName, String phone, Role role, UserStatus status);

    Renter requireRenterByUserId(Long userId);
}
