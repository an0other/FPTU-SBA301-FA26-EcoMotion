package com.fptufa26.ecomotion.seed;

import com.fptufa26.ecomotion.common.crypto.DeterministicCipher;
import com.fptufa26.ecomotion.user.entity.EkycStatus;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.entity.ProviderStatus;
import com.fptufa26.ecomotion.user.entity.ProviderType;
import com.fptufa26.ecomotion.user.entity.Renter;
import com.fptufa26.ecomotion.user.entity.Role;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.entity.UserStatus;
import com.fptufa26.ecomotion.user.repository.ProviderRepository;
import com.fptufa26.ecomotion.user.repository.RenterRepository;
import com.fptufa26.ecomotion.user.repository.UserRepository;
import com.fptufa26.ecomotion.vehicle.entity.FuelType;
import com.fptufa26.ecomotion.vehicle.entity.PlanStatus;
import com.fptufa26.ecomotion.vehicle.entity.PlanType;
import com.fptufa26.ecomotion.vehicle.entity.RentalPlan;
import com.fptufa26.ecomotion.vehicle.entity.Transmission;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.entity.VehicleImage;
import com.fptufa26.ecomotion.vehicle.entity.VehicleStatus;
import com.fptufa26.ecomotion.vehicle.repository.RentalPlanRepository;
import com.fptufa26.ecomotion.vehicle.repository.VehicleImageRepository;
import com.fptufa26.ecomotion.vehicle.repository.VehicleRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Dev-profile seed data.
 *
 * <p>Display strings are written as ASCII-safe Unicode escapes on purpose. The source file used to
 * carry literal Vietnamese text and got mangled to mojibake by a non-UTF-8 editor/pipeline, which
 * silently corrupted seeded names and addresses (javac accepts the replacement characters without
 * complaint, so tests stayed green). Escapes keep the file pure ASCII and immune to that class of
 * bug regardless of the reader's encoding.
 *
 * <p>The rentable fleet is driven by the photos in {@code images/}, whose layout is
 * {@code images/{providerUserId}/{vehicleId}/{file}}. That makes the ids part of the contract:
 * provider user ids must be 2 and 4, and vehicle ids 1-8, or the {@code vehicle_image.image_url}
 * rows point at folders that do not exist.
 *
 * <p>So both accounts and vehicles are inserted with fixed primary keys through native statements.
 * A JPA {@code save}/{@code persist} cannot pin an id here: {@code User.id} and {@code Vehicle.id}
 * are {@code GenerationType.IDENTITY}, so Hibernate either treats a pre-set id as a detached entity
 * (issuing an UPDATE that fails with {@code StaleObjectStateException}) or rejects it outright
 * ({@code Detached entity passed to persist}). Native INSERT always writes the row we asked for.
 *
 * <p>Account layout (fixed):
 * <ul>
 *   <li>1 - renter01@ecomotion.vn (RENTER)</li>
 *   <li>2 - provider.a@ecomotion.vn (PROVIDER) owns images/2/{1..4}</li>
 *   <li>3 - admin01@ecomotion.vn (ADMIN)</li>
 *   <li>4 - provider.b@ecomotion.vn (PROVIDER) owns images/4/{5..8}</li>
 *   <li>5 - renter.demo@ecomotion.vn (RENTER)</li>
 * </ul>
 *
 * <p>Ratings and prices are varied per vehicle on purpose (see the call sites in
 * {@link #run(ApplicationArguments)}): the fleet used to carry one flat 4.80 / 12 reviews and the
 * same three prices on every car, which left the rating badge, the "sort by rating" control and the
 * price slider with nothing to distinguish. One car is seeded unrated, and the schema forbids a NULL
 * rating, so "no reviews yet" means {@code rating_count = 0} together with {@code rating_avg = 0}.
 *
 * <p>Everything here is idempotent - each block is skipped when the row already exists, so
 * re-running is safe. This seeder is the single source of demo data: it does not rely on
 * {@code db/schema.sql} being executed, because Hibernate creates the schema from the entity
 * mappings ({@code ddl-auto=create-drop} in dev/test).
 *
 * <p>Because the ids are pinned by hand, the database's identity counters never move and have to be
 * realigned before the application inserts anything of its own - otherwise the first registration
 * collides with the seeded renter at id 1. {@link #realignPinnedIdentitySequences()} at the end of
 * {@link #run(ApplicationArguments)} takes care of it; read its Javadoc before adding a new pinned
 * entity.
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataSeeder implements ApplicationRunner {

    public static final String DEFAULT_PASSWORD = "EcoMotion@123";

    /** Provider user ids are pinned to the folder names under {@code images/}. */
    private static final long PROVIDER_A_USER_ID = 2L;
    private static final long PROVIDER_B_USER_ID = 4L;

    // Vietnamese display strings, escaped so this file stays pure ASCII.
    private static final String NAME_PROVIDER_A = "Nguy\u1ec5n V\u0103n A";
    private static final String NAME_PROVIDER_B = "Tr\u1ea7n Th\u1ecb B";
    private static final String NAME_RENTER = "Ph\u1ea1m Thu H\u00e0";
    private static final String ADDRESS_A = "12 Nguy\u1ec5n Hu\u1ec7, Qu\u1eadn 1, TP. H\u1ed3 Ch\u00ed Minh";
    private static final String ADDRESS_B = "45 L\u00ea L\u1ee3i, Qu\u1eadn 3, TP. H\u1ed3 Ch\u00ed Minh";
    private static final String COLOR_WHITE = "Tr\u1eafng";
    private static final String COLOR_RED = "\u0110\u1ecf";
    private static final String COLOR_BLACK = "\u0110en";
    private static final String DESC_VF6 = "Xe \u0111i\u1ec7n 5 ch\u1ed7, ph\u00f9 h\u1ee3p \u0111i n\u1ed9i th\u00e0nh v\u00e0 l\u00e2n c\u1eadn.";
    private static final String DESC_VF3 = "Xe \u0111i\u1ec7n c\u1ee1 nh\u1ecf, ti\u1ebft ki\u1ec7m, d\u1ec5 di chuy\u1ec3n trong ph\u1ed1.";
    private static final String DESC_CIVIC = "Xe th\u1ec3 thao s\u1ed1 s\u00e0n, ph\u00f9 h\u1ee3p kh\u00e1ch th\u00edch tr\u1ea3i nghi\u1ec7m l\u00e1i.";
    private static final String DESC_CAMRY = "Sedan hybrid h\u1ea1ng D, r\u1ed9ng r\u00e3i cho gia \u0111\u00ecnh v\u00e0 c\u00f4ng t\u00e1c.";
    private static final String DESC_BMW = "Sedan sang tr\u1ecdng, ph\u00f9 h\u1ee3p \u0111\u01b0a \u0111\u00f3n \u0111\u1ed1i t\u00e1c v\u00e0 s\u1ef1 ki\u1ec7n.";
    private static final String COLOR_SILVER = "B\u1ea1c";
    private static final String COLOR_GREY = "X\u00e1m";
    private static final String DESC_PORSCHE = "Coupe th\u1ec3 thao 2 c\u1eeda, ph\u00f9 h\u1ee3p kh\u00e1ch y\u00eau c\u1ea3m gi\u00e1c l\u00e1i.";
    private static final String DESC_CAMRY_XSE = "Sedan hybrid h\u1ea1ng D b\u1ea3n XSE, r\u1ed9ng r\u00e3i v\u00e0 ti\u1ebft ki\u1ec7m.";
    private static final String DESC_RANGE_ROVER = "SUV h\u1ea1ng sang, n\u1ed9i th\u1ea5t r\u1ed9ng r\u00e3i, ph\u00f9 h\u1ee3p h\u00e0nh tr\u00ecnh d\u00e0i.";

    // Deposit taken up front. Still one value per plan, because nothing renders it yet - the renter
    // card quotes slot_price only. NOTE: db/schema.sql's ck_rental_plan_upfront wants this column
    // NULL for every plan except YEARLY, so these three values would be rejected by that file. The
    // mismatch predates this seeder and is currently harmless: dev/test build the schema from the
    // entity mappings (ddl-auto=create-drop) and Hibernate emits no CHECK constraints.
    private static final String UPFRONT_HOURLY = "500000.00";
    private static final String UPFRONT_DAILY = "2000000.00";
    private static final String UPFRONT_MONTHLY = "20000000.00";

    private final UserRepository userRepository;
    private final ProviderRepository providerRepository;
    private final RenterRepository renterRepository;
    private final VehicleRepository vehicleRepository;
    private final VehicleImageRepository vehicleImageRepository;
    private final RentalPlanRepository rentalPlanRepository;
    private final PasswordEncoder passwordEncoder;
    private final DeterministicCipher cipher;
    private final EntityManager entityManager;

    /**
     * The three active plan prices for one vehicle.
     *
     * <p>Grouped into a record so the {@code seedVehicle} call sites still read as
     * identity -&gt; specs -&gt; rating -&gt; money -&gt; photos -&gt; description instead of becoming an
     * eighteen-argument wall of bare strings, where a swapped hourly/daily price would compile
     * happily and only show up as wrong numbers in the UI.
     */
    private record PlanPrices(String hourly, String daily, String monthly) {
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // ---------------------------------------------------------------- accounts (ids pinned)
        seedUser(1L, "renter01@ecomotion.vn", "0901000001", "Renter 01", Role.RENTER);
        // Provider A sits on user 2 so it owns images/2/*.
        seedUser(PROVIDER_A_USER_ID, "provider.a@ecomotion.vn", "0901000010", NAME_PROVIDER_A, Role.PROVIDER);
        seedUser(3L, "admin01@ecomotion.vn", "0901000003", "Admin 01", Role.ADMIN);
        // Provider B sits on user 4 so it owns images/4/5.
        seedUser(PROVIDER_B_USER_ID, "provider.b@ecomotion.vn", "0901000011", NAME_PROVIDER_B, Role.PROVIDER);
        seedUser(5L, "renter.demo@ecomotion.vn", "0901000012", NAME_RENTER, Role.RENTER);

        // ---------------------------------------------------------------- provider profiles
        User providerUserA = userRepository.findById(PROVIDER_A_USER_ID).orElseThrow();
        User providerUserB = userRepository.findById(PROVIDER_B_USER_ID).orElseThrow();
        Provider providerA = seedProvider(providerUserA, "EcoMotion Depot A", ADDRESS_A, "02873000010");
        Provider providerB = seedProvider(providerUserB, "EcoMotion Depot B", ADDRESS_B, "02873000011");

        // ---------------------------------------------------------------- renter profile
        seedRenterProfile(userRepository.findById(5L).orElseThrow());

        // ---------------------------------------------------------------- vehicles + images + plans
        seedVehicle(providerA.getId(), 1L, PROVIDER_A_USER_ID, "VinFast", "VF 6", 2024, 5, Transmission.AUTOMATIC,
                COLOR_WHITE, FuelType.EV, "51A-11111",
                "4.70", 24, new PlanPrices("180000.00", "1100000.00", "16500000.00"),
                List.of(
                        "VinFast-VF6-VF7-Bangalore-Electric-Cars_20250628_114343463-750x375.webp",
                        "VinFast-VF6-VF7-Bangalore-Electric-Cars_20250628_114403238-1024x576.webp",
                        "VinFast-VF6-VF7-Bangalore-Electric-Cars_20250628_114455179-1024x576.webp"),
                DESC_VF6);

        seedVehicle(providerA.getId(), 2L, PROVIDER_A_USER_ID, "VinFast", "VF 3", 2024, 4, Transmission.AUTOMATIC,
                "Xanh", FuelType.EV, "51A-22222",
                "4.90", 41, new PlanPrices("150000.00", "900000.00", "13500000.00"),
                List.of(
                        "VF3_Rear 34_Driver_Side_Wheel_Cover1.webp",
                        "rear-left-view-121.webp",
                        "vf3-6.webp",
                        "vf3_exterior-shot-13-1-2.webp"),
                DESC_VF3);

        seedVehicle(providerA.getId(), 3L, PROVIDER_A_USER_ID, "Honda", "Civic Type R", 2023, 5, Transmission.MANUAL,
                COLOR_RED, FuelType.GASOLINE, "51A-33333",
                "4.60", 9, new PlanPrices("250000.00", "1600000.00", "22000000.00"),
                List.of(
                        "43609-a63d66d270d9ac60349da19c686f83d9.webp",
                        "43842-4edd2197b736d8fb1192cf41a64b8983.webp",
                        "e66cf80620599ab536e4e909fa9ef219.webp",
                        "free-photo-of-red-honda-civic-type-r.webp"),
                DESC_CIVIC);

        seedVehicle(providerA.getId(), 4L, PROVIDER_A_USER_ID, "Toyota", "Camry", 2025, 5, Transmission.AUTOMATIC,
                COLOR_BLACK, FuelType.HYBRID, "51A-44444",
                "4.85", 17, new PlanPrices("220000.00", "1400000.00", "19000000.00"),
                List.of(
                        "2025-toyota-camry-xse-awd-149-66993cc939929.webp",
                        "ToyotaCamry-3720260323_171128.webp",
                        "camry-exterior-closed-hood-bonnet.webp",
                        "camry-exterior-right-front-three-quarter-7.webp"),
                DESC_CAMRY);

        seedVehicle(providerB.getId(), 5L, PROVIDER_B_USER_ID, "BMW", "320i M Sport", 2023, 5, Transmission.AUTOMATIC,
                COLOR_BLACK, FuelType.GASOLINE, "51A-55555",
                "4.95", 33, new PlanPrices("320000.00", "2000000.00", "26000000.00"),
                List.of(
                        "71286676-bmw-320i-black-and-white.webp",
                        "BMW-320i-M-Sport-10th-Anniversary-Edition-29.webp",
                        "BMW-320i-M-Sport-830x415.webp",
                        "wp7439603.webp"),
                DESC_BMW);

        seedVehicle(providerB.getId(), 6L, PROVIDER_B_USER_ID, "Porsche", "911 Carrera", 2024, 4, Transmission.AUTOMATIC,
                COLOR_SILVER, FuelType.GASOLINE, "51A-66666",
                // Dearest car by the hour, but only mid-pack by the month: the provider discounts
                // long-term rentals hard to get an exotic off the lot. Kept deliberately
                // non-proportional so the price sort has to read the SELECTED plan's price - with
                // proportional prices every plan would rank the fleet identically and a sort that
                // ignored the plan filter would still look correct.
                "5.00", 6, new PlanPrices("700000.00", "4500000.00", "25000000.00"),
                List.of(
                        "360_F_469231916_yBXAlwxLVTAvJ5JkyE2PdnjCOXUp2wwE.webp",
                        "Feature-650x420-1-13-8-500x323.webp",
                        "filters_format(avif).webp",
                        "filters_format(webp).webp"),
                DESC_PORSCHE);

        seedVehicle(providerB.getId(), 7L, PROVIDER_B_USER_ID, "Toyota", "Camry XSE", 2025, 5, Transmission.AUTOMATIC,
                COLOR_SILVER, FuelType.HYBRID, "51A-77777",
                "4.40", 12, new PlanPrices("240000.00", "1500000.00", "20000000.00"),
                List.of(
                        "2025-toyota-camry-xse-awd-149-66993cc939929.webp",
                        "ToyotaCamry-2620260323_171018.webp",
                        "ToyotaCamry-3720260323_171128.webp",
                        "camry-exterior-right-front-three-quarter-7.webp"),
                DESC_CAMRY_XSE);

        seedVehicle(providerB.getId(), 8L, PROVIDER_B_USER_ID, "Land Rover", "Range Rover Sport", 2023, 5, Transmission.AUTOMATIC,
                COLOR_GREY, FuelType.GASOLINE, "51A-88888",
                // Deliberately unrated: ck_vehicle_rating forbids NULL, so "no reviews yet" is a
                // zero average with a zero count. The card must render that as "Mới", not "0.0".
                "0.00", 0, new PlanPrices("450000.00", "2800000.00", "38000000.00"),
                List.of(
                        "2022-range-rover-se-lwb-150-1665593833.webp",
                        "L461_23MY_032_GLHD_BORASCO_GREY.webp",
                        "RANGE-ROVER-ELECTRIC-FRONT-3-4-02_09_26_1.jpg_auto=format,compress&fit=crop&h=303&ixlib=php-3.3.webp",
                        "range-rover-sport-in-monte-carlo-monaco.webp"),
                DESC_RANGE_ROVER);

        // Every id above was written by hand, so the database's identity counters are still at 1.
        // Hand them back to the application before it inserts anything - see the method Javadoc.
        realignPinnedIdentitySequences();
    }

    // ------------------------------------------------------------------ identity realignment

    /**
     * Pushes the identity counter of every hand-pinned table past the ids we just inserted.
     *
     * <p>{@code users.id} and {@code vehicle.id} are {@code bigint GENERATED BY DEFAULT AS IDENTITY}
     * (see {@code db/schema.sql}; Hibernate emits the same DDL for {@code GenerationType.IDENTITY}).
     * {@code BY DEFAULT} is exactly what lets {@code seedUser} and {@code seedVehicle} write an
     * explicit id - and it is also why that insert does <em>not</em> advance the counter. So after
     * seeding ids 1-5 the next Hibernate insert still asks the database for id 1 and dies on
     * {@code users_pkey}. That was the registration bug: with {@code ddl-auto=create-drop} the
     * schema (and therefore the counter) is recreated at every boot, so every new account collided
     * with the seeded renter at id 1.
     *
     * <p>{@code ALTER TABLE ... ALTER COLUMN id RESTART WITH n} is used because it is the one form
     * both PostgreSQL and H2 accept for an identity column, and an IDENTITY column keeps no state
     * on the Hibernate side, so nothing else needs resetting. The max id is read first and
     * interpolated as a literal because {@code RESTART WITH} only accepts a constant, not a
     * subquery.
     *
     * <p>Add a table to this list whenever a new entity starts being inserted with a pinned id.
     */
    private void realignPinnedIdentitySequences() {
        realignIdentitySequence("users");
        realignIdentitySequence("vehicle");
    }

    private void realignIdentitySequence(String table) {
        Number maxId = (Number) entityManager
                .createNativeQuery("SELECT COALESCE(MAX(id), 0) FROM " + table)
                .getSingleResult();
        entityManager
                .createNativeQuery("ALTER TABLE " + table + " ALTER COLUMN id RESTART WITH "
                        + (maxId.longValue() + 1L))
                .executeUpdate();
    }

    // ------------------------------------------------------------------ users & profiles

    /**
     * Inserts a user with a pinned id, or returns the existing row for that email.
     *
     * <p>Lookup is by email (the natural key), then the id is asserted to line up: if the email
     * already exists under a different id the pinned layout is broken and we fail loudly rather
     * than seed images that point at the wrong folder.
     */
    private User seedUser(long id, String email, String phone, String fullName, Role role) {
        User existing = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (existing != null) {
            if (existing.getId() != id) {
                throw new IllegalStateException("Seed user " + email + " exists with id "
                        + existing.getId() + " but images requires id " + id);
            }
            return existing;
        }

        if (userRepository.findById(id).isEmpty()) {
            // users.phone is @Convert(EncryptedStringConverter.class). A native INSERT bypasses
            // Hibernate's converter, so the ciphertext has to be produced here by hand - writing
            // the plaintext would make every later read blow up in decrypt() ("Could not decrypt
            // value"). The cipher is deterministic, so the uk_users_phone constraint still holds.
            String phoneCiphertext = cipher.encrypt(phone);
            entityManager.createNativeQuery("""
                            INSERT INTO users
                                (id, email, phone, password_hash, full_name, avatar_url, role, status,
                                 ekyc_status, created_at, updated_at)
                            VALUES
                                (:id, :email, :phone, :passwordHash, :fullName, NULL, :role, :status,
                                 :ekycStatus, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                            """)
                    .setParameter("id", id)
                    .setParameter("email", email)
                    .setParameter("phone", phoneCiphertext)
                    .setParameter("passwordHash", passwordEncoder.encode(DEFAULT_PASSWORD))
                    .setParameter("fullName", fullName)
                    .setParameter("role", role.name())
                    .setParameter("status", UserStatus.ACTIVE.name())
                    .setParameter("ekycStatus", EkycStatus.VERIFIED.name())
                    .executeUpdate();
        }

        return userRepository.findById(id).orElseThrow();
    }

    /**
     * Creates the provider profile for {@code user}, or returns the existing one.
     *
     * <p>The depot address is what the renter list shows as the pickup point, so an existing row
     * that predates the field (or was created blank by another flow) gets backfilled here instead
     * of silently rendering cards without a location.
     */
    private Provider seedProvider(User user, String displayName, String depotAddress, String hotline) {
        Provider existing = providerRepository.findByUser_Id(user.getId()).orElse(null);
        if (existing != null) {
            if (existing.getDepotAddress() == null || existing.getDepotAddress().isBlank()) {
                existing.setDepotAddress(depotAddress);
                if (existing.getHotline() == null || existing.getHotline().isBlank()) {
                    existing.setHotline(hotline);
                }
                return providerRepository.save(existing);
            }
            return existing;
        }

        Provider provider = new Provider();
        provider.setUser(user);
        provider.setProviderType(ProviderType.BUSINESS);
        provider.setDisplayName(displayName);
        provider.setHotline(hotline);
        provider.setDepotAddress(depotAddress);
        provider.setCommissionRate(new BigDecimal("0.1000"));
        provider.setDepositRatio(new BigDecimal("0.3000"));
        provider.setStatus(ProviderStatus.APPROVED);
        provider.setRatingAvg(new BigDecimal("5.00"));
        provider.setRatingCount(2);
        return providerRepository.save(provider);
    }

    private void seedRenterProfile(User user) {
        if (renterRepository.existsByUser_Id(user.getId())) {
            return;
        }
        Renter renter = new Renter();
        renter.setUser(user);
        renterRepository.save(renter);
    }

    // ------------------------------------------------------------------ fleet

    /**
     * Creates one vehicle with its images and active plans. {@code providerUserId} is the folder
     * segment in {@code images/}, used to build each image URL as
     * {@code /images/{providerUserId}/{vehicleId}/{file}}.
     *
     * <p>The vehicle row is inserted with a native statement so the primary key can be pinned to
     * the folder name. A JPA {@code save}/{@code persist} cannot do that here: {@code Vehicle.id}
     * is {@code GenerationType.IDENTITY}, so Hibernate rejects an entity that already carries an id
     * (detached-entity / stale-row errors). Image {@code displayOrder} follows the order of
     * {@code imageFiles}.
     *
     * @param ratingAvg   decimal string; {@code "0.00"} means "not rated yet". The schema's
     *                    {@code ck_vehicle_rating} forbids a NULL rating, so an unrated car is
     *                    {@code 0} with a zero count rather than NULL.
     * @param ratingCount number of reviews behind {@code ratingAvg}; must be 0 when the average is 0.
     * @param prices      the three plan prices, quoted on the renter card for this vehicle.
     */
    private void seedVehicle(Long providerId, Long vehicleId, Long providerUserId,
                             String brand, String model, int year, int seats,
                             Transmission transmission, String color, FuelType fuelType,
                             String licensePlate,
                             String ratingAvg, int ratingCount, PlanPrices prices,
                             List<String> imageFiles, String description) {
        if (vehicleRepository.findById(vehicleId).isEmpty()) {
            entityManager.createNativeQuery("""
                            INSERT INTO vehicle
                                (id, provider_id, license_plate, chassis_no, engine_no, brand, model,
                                 manufacture_year, seat_count, transmission, color, fuel_type, status,
                                 maintenance_buffer_minutes, rating_avg, rating_count, created_at, updated_at)
                            VALUES
                                (                                 :id, :providerId, :licensePlate, :chassisNo, :engineNo, :brand, :model,
                                 :year, :seats, :transmission, :color, :fuelType, :status,
                                 60, :ratingAvg, :ratingCount, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                            """)
                    .setParameter("id", vehicleId)
                    .setParameter("providerId", providerId)
                    .setParameter("licensePlate", licensePlate)
                    .setParameter("chassisNo", "CHASSIS-" + brand.toUpperCase() + "-" + vehicleId)
                    .setParameter("engineNo", "ENGINE-" + brand.toUpperCase() + "-" + vehicleId)
                    .setParameter("brand", brand)
                    .setParameter("model", model)
                    .setParameter("year", year)
                    .setParameter("seats", seats)
                    .setParameter("transmission", transmission.name())
                    .setParameter("color", color)
                    .setParameter("fuelType", fuelType.name())
                    .setParameter("status", VehicleStatus.APPROVED.name())
                    // Bound as a BigDecimal, not the String: PostgreSQL will not coerce a varchar
                    // bind into a numeric column the way H2 quietly does.
                    .setParameter("ratingAvg", new BigDecimal(ratingAvg))
                    .setParameter("ratingCount", ratingCount)
                    .executeUpdate();
        }

        // Reload through the persistence context so images/plans attach to a managed instance.
        Vehicle vehicle = vehicleRepository.findById(vehicleId).orElseThrow();

        seedImages(vehicle, providerUserId, imageFiles, description);
        seedPlans(vehicle, prices);
    }

    private void seedImages(Vehicle vehicle, Long providerUserId, List<String> imageFiles, String description) {
        if (!vehicleImageRepository.findByVehicle_IdOrderByDisplayOrderAsc(vehicle.getId()).isEmpty()) {
            return;
        }
        int order = 0;
        for (String file : imageFiles) {
            VehicleImage image = new VehicleImage();
            image.setVehicle(vehicle);
            image.setImageUrl("/images/" + providerUserId + "/" + vehicle.getId() + "/" + encodePathSegment(file));
            image.setDisplayOrder(order);
            image.setCaption(order == 0 ? description : null);
            vehicleImageRepository.save(image);
            order++;
        }
    }

    /**
     * Seeds three active plans per vehicle (hourly / daily / monthly) if none exist yet, so the
     * renter list always shows a price range and the plan filter has something to match.
     *
     * <p>The prices come from the vehicle's own {@link PlanPrices}. They used to be the same three
     * figures for the whole fleet, which made the price sort and the price-ceiling filter no-ops:
     * every card printed the same number.
     */
    private void seedPlans(Vehicle vehicle, PlanPrices prices) {
        if (!rentalPlanRepository.findByVehicle_IdOrderByIdAsc(vehicle.getId()).isEmpty()) {
            return;
        }
        createPlan(vehicle, PlanType.HOURLY, 240, prices.hourly(), UPFRONT_HOURLY);
        createPlan(vehicle, PlanType.DAILY, 1440, prices.daily(), UPFRONT_DAILY);
        createPlan(vehicle, PlanType.MONTHLY, 43200, prices.monthly(), UPFRONT_MONTHLY);
    }

    private void createPlan(Vehicle vehicle, PlanType planType, int slotLengthMinutes,
                            String slotPrice, String upfrontAmount) {
        RentalPlan plan = new RentalPlan();
        plan.setVehicle(vehicle);
        plan.setPlanType(planType);
        plan.setSlotLengthMinutes(slotLengthMinutes);
        plan.setSlotPrice(new BigDecimal(slotPrice));
        plan.setUpfrontAmount(new BigDecimal(upfrontAmount));
        plan.setStatus(PlanStatus.ACTIVE);
        rentalPlanRepository.save(plan);
    }

    /** Percent-encodes the characters that actually appear in the prepared filenames (spaces). */
    private static String encodePathSegment(String segment) {
        return segment.replace(" ", "%20");
    }
}
