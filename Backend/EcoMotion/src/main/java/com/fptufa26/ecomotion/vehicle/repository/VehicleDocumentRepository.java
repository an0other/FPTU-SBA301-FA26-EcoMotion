package com.fptufa26.ecomotion.vehicle.repository;

import com.fptufa26.ecomotion.vehicle.entity.VehicleDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VehicleDocumentRepository extends JpaRepository<VehicleDocument, Long> {

    List<VehicleDocument> findByVehicle_Id(Long vehicleId);
}
