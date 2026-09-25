package com.fptufa26.ecomotion.common.security;

import com.fptufa26.ecomotion.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bridges Spring Security to the {@code user} module's public {@link UserService}.
 *
 * <p>{@code common/security} is cross-cutting and is therefore allowed to depend on
 * {@code UserService} (plan §4).</p>
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserService userService;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return new SecurityUser(userService.loadForAuthentication(email));
    }

    @Transactional(readOnly = true)
    public UserDetails loadUserById(Long userId) throws UsernameNotFoundException {
        return userService.findById(userId)
                .map(SecurityUser::new)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: id=" + userId));
    }
}
