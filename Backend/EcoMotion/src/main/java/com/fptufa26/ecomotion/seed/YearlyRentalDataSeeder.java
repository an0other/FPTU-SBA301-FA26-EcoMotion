package com.fptufa26.ecomotion.seed;

import com.fptufa26.ecomotion.booking.entity.*;
import com.fptufa26.ecomotion.booking.enums.*;
import com.fptufa26.ecomotion.booking.policy.YearlyRentalRules;
import com.fptufa26.ecomotion.common.storage.service.FileStorageService;
import com.fptufa26.ecomotion.common.storage.service.DocumentStorageService;
import com.fptufa26.ecomotion.finance.entity.PaymentRequest;
import com.fptufa26.ecomotion.finance.enums.*;
import com.fptufa26.ecomotion.finance.service.PaymentRequestService;
import com.fptufa26.ecomotion.schedule.entity.VehicleSchedule;
import com.fptufa26.ecomotion.schedule.enums.*;
import com.fptufa26.ecomotion.user.entity.Renter;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.repository.RenterRepository;
import com.fptufa26.ecomotion.user.repository.UserRepository;
import com.fptufa26.ecomotion.vehicle.entity.*;
import com.fptufa26.ecomotion.vehicle.enums.*;
import com.fptufa26.ecomotion.vehicle.repository.VehicleRepository;
import com.fptufa26.ecomotion.vehicle.repository.VehicleImageRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Component
@Profile("dev & !prod")
@RequiredArgsConstructor
@Slf4j
public class YearlyRentalDataSeeder {
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final BigDecimal YEAR_PRICE = new BigDecimal("181000000");
    private static final BigDecimal DEPOSIT = new BigDecimal("50000000");
    private static final long INITIAL_KM = 10000L;
    private final UserRepository userRepository;
    private final RenterRepository renterRepository;
    private final VehicleRepository vehicleRepository;
    private final VehicleImageRepository vehicleImageRepository;
    private final FileStorageService fileStorageService;
    private final DocumentStorageService documentStorageService;
    private final PaymentRequestService payments;
    private final EntityManager entityManager;
    private final Clock clock;
    @Value("${booking.yearly.rounding-unit:100000}")
    private BigDecimal roundingUnit;
    @Transactional
    public void seed() {
        YearlyRentalRules.installments(YEAR_PRICE, roundingUnit);
        Vehicle template = vehicleRepository.findByLicensePlate("51A-11111").orElseThrow();
        LocalDate today = LocalDate.now(clock.withZone(VIETNAM_ZONE));
        LocalDate activeStart = today.minusMonths(3).plusDays(3);
        demoVehicle(template, 1);
        demoVehicle(template, 9);
        Renter active = findRenter("renter.yearly.monthly@ecomotion.vn");
        Renter closing = findRenter("renter.yearly.happy-end@ecomotion.vn");
        seedLicense(findRenter("renter.yearly.new@ecomotion.vn"), "B2");
        seedLicense(active, "B2");
        seedLicense(closing, "B2");
        seedBooking(active, template, 2, "MONTHLY", activeStart, 3);
        seedBooking(closing, template, 10, "HAPPY-END", today.minusMonths(12), 12);
        entityManager.flush();
        log.info("Yearly demo ready: new, monthly and happy-end renters share provider.a@ecomotion.vn. Continue reports and replacement on MONTHLY before requesting its final return. Existing progress is preserved.");
    }

    private Renter findRenter(String email) {
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        return renterRepository.findByUser_Id(user.getId()).orElseThrow();
    }

    private Vehicle demoVehicle(Vehicle template, int number) {
        String plate = "51Y-" + String.format("%05d", number);
        var existing = vehicleRepository.findByLicensePlate(plate);
        if (existing.isPresent()) return existing.get();
        Vehicle v = new Vehicle();
        v.setProvider(template.getProvider());
        v.setLicensePlate(plate);
        v.setChassisNo("YEARLY-DEMO-V2-CHASSIS-" + number);
        v.setEngineNo("YEARLY-DEMO-V2-ENGINE-" + number);
        v.setBrand(template.getBrand());
        v.setModel(template.getModel() + " Demo Y" + number);
        v.setManufactureYear(template.getManufactureYear());
        v.setSeatCount(template.getSeatCount());
        v.setTransmission(template.getTransmission());
        v.setColor(template.getColor());
        v.setFuelType(template.getFuelType());
        v.setStatus(VehicleStatus.APPROVED);
        v.setMaintenanceBufferMinutes(template.getMaintenanceBufferMinutes());
        v.setDispatchLatitude(template.getDispatchLatitude());
        v.setDispatchLongitude(template.getDispatchLongitude());
        entityManager.persist(v);
        for (var source : vehicleImageRepository.findByVehicle_IdOrderByDisplayOrderAsc(template.getId())) {
            VehicleImage image = new VehicleImage();
            image.setVehicle(v);
            image.setImageUrl(source.getImageUrl());
            image.setDisplayOrder(source.getDisplayOrder());
            image.setCaption("YEARLY DEMO - " + plate);
            entityManager.persist(image);
        }
        RentalPlan plan = new RentalPlan();
        plan.setVehicle(v);
        plan.setPlanType(PlanType.YEARLY);
        plan.setSlotLengthMinutes(525600);
        plan.setSlotPrice(YEAR_PRICE);
        plan.setUpfrontAmount(DEPOSIT);
        plan.setStatus(PlanStatus.ACTIVE);
        entityManager.persist(plan);
        return v;
    }

    private void seedBooking(Renter renter, Vehicle template, int number, String scenario, LocalDate start, int paidMonths) {
        String code = "EM-Y2-" + scenario;
        Booking existing = entityManager.createQuery("select b from Booking b where b.bookingCode=:code", Booking.class)
                .setParameter("code", code).getResultStream().findFirst().orElse(null);
        if (existing != null) return;
        Vehicle v = demoVehicle(template, number);
        RentalPlan plan = entityManager.createQuery("select p from RentalPlan p where p.vehicle.id=:id and p.planType=:type", RentalPlan.class)
                .setParameter("id", v.getId()).setParameter("type", PlanType.YEARLY).getSingleResult();
        Instant startedAt = start.atStartOfDay(VIETNAM_ZONE).toInstant();
        Instant createdAt = start.minusDays(7).atStartOfDay(VIETNAM_ZONE).toInstant();
        Booking b = new Booking();
        b.setBookingCode(code);
        b.setVehicle(v);
        b.setPlan(plan);
        b.setRenter(renter);
        b.setPickupAddress(v.getProvider().getDepotAddress());
        b.setPickupLatitude(v.getProvider().getDepotLatitude());
        b.setPickupLongitude(v.getProvider().getDepotLongitude());
        b.setPassengerCount(1);
        b.setPickupTime(startedAt);
        b.setPlannedEndAt(YearlyRentalRules.contractEnd(startedAt));
        b.setTotalAmount(YEAR_PRICE);
        b.setStatus(BookingStatus.AWAITING_DEPOSIT);
        b.setCreatedAt(createdAt);
        b.setUpdatedAt(createdAt);
        entityManager.persist(b);
        b.setContractUrl(documentStorageService.store("booking", renter.getUser().getId(),
                code + "-" + java.util.UUID.randomUUID() + ".pdf", demoContract(code), "application/pdf"));

        BookingPolicySnapshot snapshot = new BookingPolicySnapshot();
        snapshot.setBooking(b);
        snapshot.setPlanType(PlanType.YEARLY);
        snapshot.setSlotPrice(YEAR_PRICE);
        snapshot.setSlotLengthMinutes(plan.getSlotLengthMinutes());
        snapshot.setUpfrontAmount(DEPOSIT);
        snapshot.setDepositAmount(DEPOSIT);
        snapshot.setVehicleName(v.getBrand() + " " + v.getModel());
        snapshot.setVehicleManufactureYear(v.getManufactureYear());
        snapshot.setVehicleLicensePlate(v.getLicensePlate());
        snapshot.setPickupFee(BigDecimal.ZERO);
        snapshot.setCreatedAt(createdAt);
        entityManager.persist(snapshot);

        BookingCharge charge = new BookingCharge();
        charge.setBooking(b);
        charge.setChargeType(ChargeType.BASE_RENTAL);
        charge.setDescription("YEARLY DEMO V2 - tien thue 12 thang, coc rieng");
        charge.setQuantity(1);
        charge.setUnitPrice(YEAR_PRICE);
        charge.setAmount(YEAR_PRICE);
        charge.setCreatedBy(v.getProvider().getUser());
        charge.setCreatedAt(createdAt);
        entityManager.persist(charge);

        YearlyWorkflow workflow = new YearlyWorkflow();
        workflow.setBookingId(b.getId());
        workflow.setStage("TERMS_ACCEPTED");
        workflow.setTermsAccepted(true);
        workflow.setContractTotal(YEAR_PRICE);
        workflow.setSecurityDeposit(DEPOSIT);
        workflow.setRoundingUnit(roundingUnit);
        workflow.setAnnualKm(24000L);
        workflow.setExcessKmRate(new BigDecimal("5000"));
        workflow.setGraceDays(1);
        workflow.setLateDailyRate(new BigDecimal("500000"));
        workflow.setCurrentInitialKm(INITIAL_KM);
        entityManager.persist(workflow);
        entityManager.flush();

        payments.createDepositPaymentRequest(b);
        var requests = entityManager.createQuery("select p from PaymentRequest p where p.booking.id=:id", PaymentRequest.class)
                .setParameter("id", b.getId()).getResultList();
        for (PaymentRequest request : requests) {
            request.setCreatedAt(createdAt);
            if (!request.isSecurityDeposit() && request.getInstallmentNumber() > paidMonths) continue;
            Instant paidAt = request.isSecurityDeposit() ? createdAt.plusSeconds(3600)
                    : start.plusMonths(request.getInstallmentNumber() - 1L).minusDays(1).atStartOfDay(VIETNAM_ZONE).toInstant();
            if (paidAt.isAfter(clock.instant())) paidAt = clock.instant();
            request.setPaidAmount(request.getAmount());
            request.setStatus(PaymentRequestStatus.CONFIRMED);
            request.setExpiresAt(null);
            request.setConfirmedAt(paidAt);
            request.setConfirmedBy(v.getProvider().getUser());
            String reference = "Y2-" + number + "-" + request.getInstallmentNumber();
            PaymentTransaction tx = new PaymentTransaction();
            tx.setBooking(b);
            tx.setCharge(request.isSecurityDeposit() ? null : charge);
            tx.setPaymentRequest(request);
            tx.setReceivingBankAccount(request.getReceivingBankAccount());
            tx.setType(request.isSecurityDeposit() ? PaymentType.SECURITY_DEPOSIT : PaymentType.SETTLEMENT);
            tx.setDirection(PaymentDirection.IN);
            tx.setMethod(PaymentMethod.BANK_TRANSFER);
            tx.setAmount(request.getAmount());
            tx.setAppliedAmount(request.getAmount());
            tx.setStatus(PaymentStatus.PAID);
            tx.setVerificationMethod(PaymentVerificationMethod.PROVIDER_CONFIRMED);
            tx.setConfirmedBy(v.getProvider().getUser());
            tx.setTransactionCode(reference);
            tx.setGatewayRef(reference);
            tx.setBankCodeSnapshot(request.getBankCodeSnapshot());
            tx.setBankNameSnapshot(request.getBankNameSnapshot());
            tx.setBankAccountHolderSnapshot(request.getAccountHolderSnapshot());
            tx.setProcessingNote("DEMO FIXTURE ONLY - historical payment; no real bank transfer");
            tx.setPaidAt(paidAt);
            tx.setCreatedAt(paidAt);
            tx.setUpdatedAt(paidAt);
            entityManager.persist(tx);
        }
        entityManager.flush();
        entityManager.createNativeQuery("update payment_request set created_at=:createdAt where booking_id=:id")
                .setParameter("createdAt", createdAt).setParameter("id", b.getId()).executeUpdate();
        b.setStatus(BookingStatus.IN_PROGRESS);
        b.setActualStartAt(startedAt);
        b.setRenterStartConfirmedAt(startedAt);
        b.setProviderConfirmedAt(createdAt);
        TripEvidence photo = new TripEvidence();
        photo.setBooking(b);
        photo.setUploadedBy(v.getProvider().getUser());
        photo.setEvidenceType(TripEvidenceType.OTHER);
        photo.setImageUrl(vehicleImageRepository.findByVehicle_IdOrderByDisplayOrderAsc(v.getId()).stream()
                .findFirst().map(VehicleImage::getImageUrl).orElseThrow());
        photo.setNote("DEMO FIXTURE ONLY - initial handover; odometer 10000 km");
        photo.setCreatedAt(startedAt);
        photo.setRecordedAt(startedAt);
        entityManager.persist(photo);
        YearlyHandover handover = new YearlyHandover();
        handover.setBookingId(b.getId());
        handover.setStage("RECEIVED");
        handover.setInitialKm(INITIAL_KM);
        handover.setReceivedOn(start);
        handover.setDispatchedAt(startedAt);
        handover.setArrivedAt(startedAt);
        handover.setEvidenceId(photo.getId());
        handover.setEvidenceUrl(photo.getImageUrl());
        entityManager.persist(handover);
        VehicleSchedule schedule = new VehicleSchedule();
        schedule.setVehicle(v);
        schedule.setBooking(b);
        schedule.setType(ScheduleType.BOOKED);
        schedule.setStatus(ScheduleStatus.ACTIVE);
        schedule.setStartAt(startedAt);
        schedule.setEndAt(YearlyRentalRules.reservationEnd(b.getPlannedEndAt()));
        schedule.setCreatedAt(createdAt);
        schedule.setExtension(false);
        entityManager.persist(schedule);
        log.info("Created yearly demo {}: renter={}, plate={}, start={}, due={}, paidMonths={}",
                code, renter.getUser().getEmail(), v.getLicensePlate(), start,
                start.plusMonths(12), paidMonths);
    }

    private byte[] demoContract(String code) {
        String text = "DEMO ONLY - NOT A SIGNED CONTRACT - " + code;
        String stream = "BT /F1 14 Tf 40 760 Td (" + text + ") Tj ET";
        String[] objects = {
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
            "<< /Length " + stream.length() + " >>\nstream\n" + stream + "\nendstream"
        };
        StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
        int[] offsets = new int[objects.length];
        for (int i = 0; i < objects.length; i++) {
            offsets[i] = pdf.length();
            pdf.append(i + 1).append(" 0 obj\n").append(objects[i]).append("\nendobj\n");
        }
        int xref = pdf.length();
        pdf.append("xref\n0 6\n0000000000 65535 f \n");
        for (int offset : offsets) pdf.append(String.format("%010d 00000 n \n", offset));
        pdf.append("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        return pdf.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private void seedLicense(Renter renter, String licenseClass) {
        if (renter.getLicenseNo() != null && !renter.getLicenseNo().startsWith("SEED-YEARLY-")) {
            return;
        }

        LocalDate today = LocalDate.now(clock.withZone(VIETNAM_ZONE));
        if (licenseClass.equals(renter.getLicenseClass()) && renter.isLicenseVerified()
                && renter.getLicenseDateOfBirth() != null && renter.getLicenseIssueDate() != null
                && renter.getLicenseExpiry() != null && !renter.getLicenseExpiry().isBefore(today.plusYears(1))
                && renter.getLicenseFrontImagePath() != null && renter.getLicenseBackImagePath() != null) return;

        String normalizedClass = licenseClass.toUpperCase();
        String filePrefix = "yearly-demo-license-" + renter.getUser().getId();
        String frontPath = fileStorageService.store(
                "renter",
                renter.getUser().getId(),
                filePrefix + "-front.png",
                createDemoLicenseImage("FRONT", renter.getUser().getFullName(), normalizedClass),
                "image/png");
        String backPath = fileStorageService.store(
                "renter",
                renter.getUser().getId(),
                filePrefix + "-back.png",
                createDemoLicenseImage("BACK", renter.getUser().getFullName(), normalizedClass),
                "image/png");

        renter.setLicenseNo("SEED-YEARLY-" + renter.getUser().getId());
        renter.setLicenseFullName(renter.getUser().getFullName());
        renter.setLicenseDateOfBirth(renter.getDateOfBirth() == null ? LocalDate.of(1992, 4, 12) : renter.getDateOfBirth());
        renter.setLicenseClass(normalizedClass);
        renter.setLicenseIssueDate(today.minusYears(2));
        renter.setLicenseExpiry(today.plusYears(5));
        renter.setLicenseExpiryUnlimited(false);
        renter.setLicenseVerified(true);
        renter.setLicenseFrontImagePath(frontPath);
        renter.setLicenseBackImagePath(backPath);
        renterRepository.saveAndFlush(renter);
    }

    private byte[] createDemoLicenseImage(String side, String fullName, String licenseClass) {
        BufferedImage image = new BufferedImage(900, 560, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(new Color(28, 67, 93));
            graphics.fillRect(0, 0, image.getWidth(), 90);
            graphics.setColor(Color.WHITE);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 32));
            graphics.drawString("DEMO ONLY - NOT AN OFFICIAL DOCUMENT", 28, 57);
            graphics.setColor(new Color(28, 67, 93));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
            graphics.drawString("SAMPLE DRIVING LICENSE - " + side, 32, 165);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 26));
            graphics.drawString("Name: " + fullName, 32, 235);
            graphics.drawString("Class: " + licenseClass, 32, 285);
            graphics.drawString("Seed data for local development only", 32, 390);
            graphics.setColor(new Color(180, 35, 35));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34));
            graphics.drawString("NOT VALID FOR IDENTIFICATION", 32, 485);
        } finally {
            graphics.dispose();
        }

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", output)) {
                throw new IllegalStateException("Could not encode demo driving-license image");
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create demo driving-license image", exception);
        }
    }

}
