package com.fptufa26.ecomotion.auth.mapper;

import com.fptufa26.ecomotion.auth.dto.response.CurrentUserResponse;
import com.fptufa26.ecomotion.auth.dto.response.LoginResponse;
import com.fptufa26.ecomotion.auth.dto.response.RefreshResponse;
import com.fptufa26.ecomotion.user.dto.response.UserResponse;
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
                user.getFullName(),
                user.getPhone(),
                user.getRole(),
                user.getStatus(),
                user.getEkycStatus());
    }

    public UserResponse toUserResponse(User user) {
        return new UserResponse(
                user.getUserId(),
                user.getEmail(),
                user.getRole(),
                user.getStatus());
    }
}
