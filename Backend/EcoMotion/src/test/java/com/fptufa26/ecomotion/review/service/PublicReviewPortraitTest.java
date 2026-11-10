package com.fptufa26.ecomotion.review.service;

import com.fptufa26.ecomotion.review.repository.ReviewRepository;
import com.fptufa26.ecomotion.user.entity.Driver;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.vehicle.service.VehicleService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PublicReviewPortraitTest {
    @Test void prefersTheSamePortraitUsedInBookingDetailsAndFallsBackToAccountAvatar() {
        var users = mock(UserService.class);
        var driver = new Driver(); var user = new User();
        user.setFullName("Demo driver"); user.setAvatarUrl("/images/avatar.png");
        driver.setUser(user); driver.setPortraitUrl("/images/portrait.png");
        when(users.findDriverById(7L)).thenReturn(Optional.of(driver));
        var service = new PublicReviewService(mock(ReviewRepository.class), mock(VehicleService.class), users);
        assertThat(service.forDriver(7L).targetImageUrl()).isEqualTo("/images/portrait.png");
        driver.setPortraitUrl(" ");
        assertThat(service.forDriver(7L).targetImageUrl()).isEqualTo("/images/avatar.png");
        driver.setUser(null);
        assertThat(service.forDriver(7L).targetImageUrl()).isNull();
    }
}
