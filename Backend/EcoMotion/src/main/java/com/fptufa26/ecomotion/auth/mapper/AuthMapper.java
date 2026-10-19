package com.fptufa26.ecomotion.auth.mapper;

import com.fptufa26.ecomotion.auth.dto.response.CurrentUserResponse;
import com.fptufa26.ecomotion.auth.dto.response.LoginResponse;
import com.fptufa26.ecomotion.auth.dto.response.RefreshResponse;
import com.fptufa26.ecomotion.user.dto.response.UserResponse;
import com.fptufa26.ecomotion.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AuthMapper {

    @Mapping(target = "tokenType", constant = "Bearer")
    @Mapping(target = "user", source = "user")
    LoginResponse toLoginResponse(User user, String accessToken, long expiresIn);

    @Mapping(target = "tokenType", constant = "Bearer")
    RefreshResponse toRefreshResponse(String accessToken, long expiresIn);

    CurrentUserResponse toCurrentUserResponse(User user);

    UserResponse toUserResponse(User user);
}
