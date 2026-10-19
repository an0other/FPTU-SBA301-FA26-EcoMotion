package com.fptufa26.ecomotion.vehicle.repository;

import com.fptufa26.ecomotion.vehicle.entity.VehicleDocument;
import com.fptufa26.ecomotion.vehicle.enums.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleDocumentRepository extends JpaRepository<VehicleDocument, Long> {

    List<VehicleDocument> findByVehicle_Id(Long vehicleId);

    Optional<VehicleDocument> findByVehicle_IdAndDocType(Long vehicleId, DocumentType docType);

    List<VehicleDocument> findByVehicle_IdIn(List<Long> vehicleIds);
}
