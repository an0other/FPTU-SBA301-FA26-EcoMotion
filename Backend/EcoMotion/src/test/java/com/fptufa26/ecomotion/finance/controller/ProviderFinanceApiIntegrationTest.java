package com.fptufa26.ecomotion.finance.controller;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.PaymentTransaction;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.enums.PaymentDirection;
import com.fptufa26.ecomotion.booking.enums.PaymentMethod;
import com.fptufa26.ecomotion.booking.enums.PaymentStatus;
import com.fptufa26.ecomotion.booking.enums.PaymentType;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.booking.repository.PaymentTransactionRepository;
import com.fptufa26.ecomotion.common.security.JwtService;
import com.fptufa26.ecomotion.finance.dto.request.UpdateBankAccountRequest;
import com.fptufa26.ecomotion.user.entity.Driver;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.entity.Renter;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.DriverStatus;
import com.fptufa26.ecomotion.user.enums.EkycStatus;
import com.fptufa26.ecomotion.user.enums.ProviderStatus;
import com.fptufa26.ecomotion.user.enums.ProviderType;
import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.user.enums.UserStatus;
import com.fptufa26.ecomotion.user.repository.DriverRepository;
import com.fptufa26.ecomotion.user.repository.ProviderRepository;
import com.fptufa26.ecomotion.user.repository.RenterRepository;
import com.fptufa26.ecomotion.user.repository.UserRepository;
import com.fptufa26.ecomotion.vehicle.entity.RentalPlan;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.enums.FuelType;
import com.fptufa26.ecomotion.vehicle.enums.PlanStatus;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import com.fptufa26.ecomotion.vehicle.enums.Transmission;
import com.fptufa26.ecomotion.vehicle.enums.VehicleStatus;
import com.fptufa26.ecomotion.vehicle.repository.RentalPlanRepository;
import com.fptufa26.ecomotion.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProviderFinanceApiIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private JwtService jwtService;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private ProviderRepository providerRepository;

        @Autowired
        private RenterRepository renterRepository;

        @Autowired
        private DriverRepository driverRepository;

        @Autowired
        private VehicleRepository vehicleRepository;

        @Autowired
        private RentalPlanRepository rentalPlanRepository;

        @Autowired
        private BookingRepository bookingRepository;

        @Autowired
        private PaymentTransactionRepository paymentTransactionRepository;

        private User providerUserA;
        private User providerUserB;
        private User renterUser;
        private User driverUser;
        private Provider providerA;
        private Provider providerB;
        private Booking bookingA;

        @BeforeEach
        void setUp() {
                providerUserA = createOrGetUser("prov.a." + UUID.randomUUID() + "@test.vn", Role.PROVIDER);
                providerUserB = createOrGetUser("prov.b." + UUID.randomUUID() + "@test.vn", Role.PROVIDER);
                renterUser = createOrGetUser("renter." + UUID.randomUUID() + "@test.vn", Role.RENTER);
                driverUser = createOrGetUser("driver." + UUID.randomUUID() + "@test.vn", Role.DRIVER);

                providerA = createProvider(providerUserA, "Fleet Alpha", "0287100001", "10 Alpha St");
                providerB = createProvider(providerUserB, "Fleet Beta", "0287100002", "20 Beta St");

                Renter renter = new Renter();
                renter.setUser(renterUser);
                renterRepository.save(renter);

                Vehicle vehicle = new Vehicle();
                vehicle.setProvider(providerA);
                vehicle.setBrand("VinFast");
                vehicle.setModel("VF 8");
                vehicle.setLicensePlate("51A-" + UUID.randomUUID().toString().substring(0, 5));
                vehicle.setChassisNo("CHS-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
                vehicle.setEngineNo("ENG-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
                vehicle.setManufactureYear(2024);
                vehicle.setSeatCount(5);
                vehicle.setTransmission(Transmission.AUTOMATIC);
                vehicle.setColor("White");
                vehicle.setFuelType(FuelType.EV);
                vehicle.setStatus(VehicleStatus.APPROVED);
                vehicle.setMaintenanceBufferMinutes(60);
                vehicleRepository.save(vehicle);

                RentalPlan plan = new RentalPlan();
                plan.setVehicle(vehicle);
                plan.setPlanType(PlanType.DAILY);
                plan.setSlotLengthMinutes(1440);
                plan.setSlotPrice(new BigDecimal("1000000.00"));
                plan.setStatus(PlanStatus.ACTIVE);
                rentalPlanRepository.save(plan);

                bookingA = new Booking();
                bookingA.setBookingCode("BK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
                bookingA.setRenter(renter);
                bookingA.setVehicle(vehicle);
                bookingA.setPlan(plan);
                bookingA.setStatus(BookingStatus.COMPLETED);
                bookingA.setPickupAddress("100 Saigon Blvd");
                bookingA.setPickupTime(Instant.now());
                bookingA.setPassengerCount(2);
                bookingA.setTotalAmount(new BigDecimal("1000000.00"));
                bookingRepository.save(bookingA);
        }

        private User createOrGetUser(String email, Role role) {
                User user = new User();
                user.setEmail(email);
                user.setFullName("User " + email);
                user.setPasswordHash("hashed_secret");
                user.setRole(role);
                user.setStatus(UserStatus.ACTIVE);
                user.setEkycStatus(EkycStatus.VERIFIED);
                return userRepository.save(user);
        }

        private Provider createProvider(User user, String displayName, String hotline, String address) {
                Provider provider = new Provider();
                provider.setUser(user);
                provider.setProviderType(ProviderType.BUSINESS);
                provider.setDisplayName(displayName);
                provider.setHotline(hotline);
                provider.setDepotAddress(address);
                provider.setCommissionRate(new BigDecimal("0.1000"));
                provider.setDepositRatio(new BigDecimal("0.3000"));
                provider.setStatus(ProviderStatus.APPROVED);
                return providerRepository.save(provider);
        }

        private String tokenFor(User user) {
                return jwtService.generateAccessToken(user);
        }

        @Test
        @DisplayName("Unauthenticated requests to bank-account return 401")
        void unauthenticatedAccessReturns401() throws Exception {
                mockMvc.perform(get("/api/provider/finance/bank-account"))
                                .andExpect(status().isUnauthorized());

                mockMvc.perform(put("/api/provider/finance/bank-account")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                                new UpdateBankAccountRequest("VCB", "0123456789", "NGUYEN VAN A"))))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Renter attempting Provider Finance returns 403 Forbidden")
        void renterAccessForbidden() throws Exception {
                String token = tokenFor(renterUser);

                mockMvc.perform(get("/api/provider/finance/bank-account")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Driver attempting Provider Finance returns 403 Forbidden")
        void driverAccessForbidden() throws Exception {
                String token = tokenFor(driverUser);

                mockMvc.perform(get("/api/provider/finance/bank-account")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Provider with no bank account gets empty state representation (200 with null data)")
        void emptyBankAccountReturnsNullData() throws Exception {
                String token = tokenFor(providerUserB);

                mockMvc.perform(get("/api/provider/finance/bank-account")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value("SUCCESS"))
                                .andExpect(jsonPath("$.data").doesNotExist());
        }

        @Test
        @DisplayName("Provider successfully updates receiving bank account and gets masked number")
        void updateBankAccountSucceeds() throws Exception {
                String token = tokenFor(providerUserA);

                UpdateBankAccountRequest request = new UpdateBankAccountRequest(
                                "VCB",
                                "0123456789012",
                                "nguyen van a");

                mockMvc.perform(put("/api/provider/finance/bank-account")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value("SUCCESS"))
                                .andExpect(jsonPath("$.data.bankCode").value("VCB"))
                                .andExpect(jsonPath("$.data.bankName").value("Vietcombank"))
                                .andExpect(jsonPath("$.data.accountNumberMasked").value("*********9012"))
                                .andExpect(jsonPath("$.data.accountHolderName").value("NGUYEN VAN A"))
                                .andExpect(jsonPath("$.data.verified").value(false));

                mockMvc.perform(get("/api/v1/provider/finance/bank-account")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.accountNumberMasked").value("*********9012"));
        }

        @Test
        @DisplayName("Updating bank account rejects unknown bank code")
        void rejectsUnknownBankCode() throws Exception {
                String token = tokenFor(providerUserA);

                UpdateBankAccountRequest request = new UpdateBankAccountRequest(
                                "UNKNOWN",
                                "0123456789012",
                                "NGUYEN VAN A");

                mockMvc.perform(put("/api/provider/finance/bank-account")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Updating bank account rejects masked account input")
        void rejectsMaskedAccountInput() throws Exception {
                String token = tokenFor(providerUserA);

                UpdateBankAccountRequest request = new UpdateBankAccountRequest(
                                "VCB",
                                "******6789",
                                "NGUYEN VAN A");

                mockMvc.perform(put("/api/provider/finance/bank-account")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Updating bank account rejects invalid account holder name")
        void rejectsInvalidAccountHolderName() throws Exception {
                String token = tokenFor(providerUserA);

                UpdateBankAccountRequest request = new UpdateBankAccountRequest(
                                "VCB",
                                "0123456789",
                                "123@#$$%^");

                mockMvc.perform(put("/api/provider/finance/bank-account")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("List payouts returns bounded pagination and filter support")
        void listPayoutsReturnsPaginatedResult() throws Exception {
                String token = tokenFor(providerUserA);

                PaymentTransaction payout = new PaymentTransaction();
                payout.setBooking(bookingA);
                payout.setType(PaymentType.PAYOUT);
                payout.setDirection(PaymentDirection.OUT);
                payout.setMethod(PaymentMethod.BANK_TRANSFER);
                payout.setAmount(new BigDecimal("1500000.00"));
                payout.setStatus(PaymentStatus.PAID);
                payout.setGatewayRef("MOCK-TEST-PAYOUT-" + UUID.randomUUID());
                payout.setBankCodeSnapshot("VCB");
                payout.setBankNameSnapshot("Vietcombank");
                payout.setBankAccountNoSnapshot("******6789");
                payout.setBankAccountHolderSnapshot("NGUYEN VAN A");
                payout.setPaidAt(Instant.now());
                paymentTransactionRepository.save(payout);

                mockMvc.perform(get("/api/provider/finance/payouts")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .param("offset", "0")
                                .param("limit", "10"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value("SUCCESS"))
                                .andExpect(jsonPath("$.data.items").isArray())
                                .andExpect(jsonPath("$.data.total").isNumber())
                                .andExpect(jsonPath("$.data.items[0].destinationBank.bankCode").value("VCB"));

                mockMvc.perform(get("/api/provider/finance/payouts")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .param("status", "PAID"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.items").isArray());
        }

        @Test
        @DisplayName("Provider B cannot access Provider A's payout (returns 404)")
        void providerCannotAccessOtherProviderPayout() throws Exception {
                PaymentTransaction payoutA = new PaymentTransaction();
                payoutA.setBooking(bookingA);
                payoutA.setType(PaymentType.PAYOUT);
                payoutA.setDirection(PaymentDirection.OUT);
                payoutA.setMethod(PaymentMethod.BANK_TRANSFER);
                payoutA.setAmount(new BigDecimal("2000000.00"));
                payoutA.setStatus(PaymentStatus.PAID);
                payoutA.setGatewayRef("MOCK-PAYOUT-" + UUID.randomUUID());
                payoutA.setPaidAt(Instant.now());
                PaymentTransaction saved = paymentTransactionRepository.save(payoutA);

                String providerBToken = tokenFor(providerUserB);

                mockMvc.perform(get("/api/provider/finance/payouts/" + saved.getId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerBToken))
                                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Provider A can view payout detail with snapshot and mock flag")
        void providerCanViewPayoutDetail() throws Exception {
                PaymentTransaction payout = new PaymentTransaction();
                payout.setBooking(bookingA);
                payout.setType(PaymentType.PAYOUT);
                payout.setDirection(PaymentDirection.OUT);
                payout.setMethod(PaymentMethod.BANK_TRANSFER);
                payout.setAmount(new BigDecimal("1200000.00"));
                payout.setStatus(PaymentStatus.PAID);
                payout.setGatewayRef("MOCK-PAYOUT-DETAIL-" + UUID.randomUUID());
                payout.setBankCodeSnapshot("TCB");
                payout.setBankNameSnapshot("Techcombank");
                payout.setBankAccountNoSnapshot("******4321");
                payout.setBankAccountHolderSnapshot("TRAN MINH KHOA");
                payout.setPaidAt(Instant.now());
                PaymentTransaction saved = paymentTransactionRepository.save(payout);

                String token = tokenFor(providerUserA);

                mockMvc.perform(get("/api/v1/provider/finance/payouts/" + saved.getId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value("SUCCESS"))
                                .andExpect(jsonPath("$.data.id").value(saved.getId()))
                                .andExpect(jsonPath("$.data.mock").value(true))
                                .andExpect(jsonPath("$.data.destinationBank.bankCode").value("TCB"))
                                .andExpect(jsonPath("$.data.destinationBank.accountNumberMasked").value("******4321"));
        }
}
