package com.fptufa26.ecomotion.user.service;

import com.fptufa26.ecomotion.user.entity.Driver;

import java.time.LocalDate;
import java.util.List;

public interface DriverAssignmentService {

    List<Driver> listEligible(Long providerId, LocalDate serviceDate);

    Driver requireEligibleForUpdate(Long providerId, Long driverId, LocalDate serviceDate);
}
