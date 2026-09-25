package com.fptufa26.ecomotion.auth.mapper;

import com.fptufa26.ecomotion.auth.dto.CurrentUserResponse;
import com.fptufa26.ecomotion.auth.dto.LoginResponse;
import com.fptufa26.ecomotion.auth.dto.RefreshResponse;
import com.fptufa26.ecomotion.user.dto.UserResponse;
import com.fptufa26.ecomotion.user.entity.User;
import org.springframework.stereotype.Component;

@Component
public class AuthMapper {

    private static final String TOKEN_TYPE = "Bearer";

    public LoginResponse toLoginResponse(User user, String accessToken, long expiresIn) {
        return new LoginResponse(accessToken, TOKEN_TYPE, expiresIn, toUserResponse(user));
    }

    public RefreshResponse toRefreshResponse(String accessToken, long expiresIn) {
        return new RefreshResponse(accessToken, TOKEN_TYPE, expiresIn);
    }

    public CurrentUserResponse toCurrentUserResponse(User user) {
        return new CurrentUserResponse(
                user.getUserId(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                user.getLastLoginAt());
    }

    public UserResponse toUserResponse(User user) {
        return new UserResponse(
                user.getUserId(),
                user.getEmail(),
                user.getRole(),
                user.getStatus());
    }
}
