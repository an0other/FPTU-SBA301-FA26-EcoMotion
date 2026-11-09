package com.fptufa26.ecomotion.common.security;

import com.fptufa26.ecomotion.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserService userService;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return principal(userService.loadForAuthentication(email));
    }

    @Transactional(readOnly = true)
    public UserDetails loadUserById(Long userId) throws UsernameNotFoundException {
        return userService.findById(userId)
                .map(this::principal)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: id=" + userId));
    }

    private SecurityUser principal(com.fptufa26.ecomotion.user.entity.User user) {
        boolean full = true;
        if (user.getRole() == com.fptufa26.ecomotion.user.enums.Role.PROVIDER) {
            full = userService.isProviderOperational(user.getId());
        }
        return new SecurityUser(user, full);
    }
}
