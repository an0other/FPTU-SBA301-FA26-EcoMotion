package com.fptufa26.ecomotion.vehicle.service;

import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.entity.VehicleLicensePlateHistory;
import com.fptufa26.ecomotion.vehicle.repository.VehicleLicensePlateHistoryRepository;
import com.fptufa26.ecomotion.vehicle.repository.VehicleRepository;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.vehicle.service.implement.LicensePlateHistoryServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LicensePlateHistoryServiceTest {

    @Mock
    private VehicleLicensePlateHistoryRepository repository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private LicensePlateHistoryServiceImpl service;

    @Test
    void recordsCanonicalOldAndNewPlateWhenProviderChangesPlate() {
        Vehicle vehicle = new Vehicle();
        User changedBy = new User();

        service.recordChange(vehicle, "51 A-123.45", "51B-999.99", changedBy);

        ArgumentCaptor<VehicleLicensePlateHistory> captor = ArgumentCaptor.forClass(VehicleLicensePlateHistory.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getPreviousPlate()).isEqualTo("51A12345");
        assertThat(captor.getValue().getNewPlate()).isEqualTo("51B99999");
        assertThat(captor.getValue().getChangedBy()).isSameAs(changedBy);
    }
}
