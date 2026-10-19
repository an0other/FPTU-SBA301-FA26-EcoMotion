package com.fptufa26.ecomotion.user.repository;

import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.EkycStatus;
import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.user.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByPhone(String phone);

    List<User> findAllByOrderByCreatedAtDesc();

    List<User> findByRoleOrderByCreatedAtDesc(Role role);

    List<User> findByStatusOrderByCreatedAtDesc(UserStatus status);

    List<User> findByEkycStatusOrderByCreatedAtDesc(EkycStatus ekycStatus);

    long countByStatus(UserStatus status);

    long countByEkycStatus(EkycStatus ekycStatus);

    long countByRole(Role role);
}
