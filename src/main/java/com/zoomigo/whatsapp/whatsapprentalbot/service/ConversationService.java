package com.zoomigo.whatsapp.whatsapprentalbot.service;

import com.zoomigo.whatsapp.whatsapprentalbot.entity.Bike;
import com.zoomigo.whatsapp.whatsapprentalbot.entity.Booking;
import com.zoomigo.whatsapp.whatsapprentalbot.entity.ChatSessionEntity;
import com.zoomigo.whatsapp.whatsapprentalbot.entity.PromoCode;
import com.zoomigo.whatsapp.whatsapprentalbot.entity.User;
import com.zoomigo.whatsapp.whatsapprentalbot.repository.BikeRepository;
import com.zoomigo.whatsapp.whatsapprentalbot.repository.BookingRepository;
import com.zoomigo.whatsapp.whatsapprentalbot.repository.ChatSessionRepository;
import com.zoomigo.whatsapp.whatsapprentalbot.repository.PromoCodeRepository;
import com.zoomigo.whatsapp.whatsapprentalbot.repository.UserRepository;
import com.zoomigo.whatsapp.whatsapprentalbot.repository.PromoCodeBikeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import com.joestelmach.natty.Parser;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.*;

@Slf4j
@Service
public class ConversationService {

    private final UserRepository userRepo;
    private final BikeRepository bikeRepo;
    private final ChatSessionRepository chatSessionRepo;
    private final BookingRepository bookingRepo;
    private final SessionResetService sessionResetService;
    private final PromoCodeRepository promoRepo;
    private final PromoCodeBikeRepository promoBikeRepo;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy");
    @Value("${app.shop-address}")
    private String shopAddress;
    @Value("${app.display-name:ZoomiGo MotoRent}")
    private String displayName;

    private CacheManager cacheManager;

    public ConversationService(UserRepository userRepo, BikeRepository bikeRepo,
                               ChatSessionRepository chatSessionRepo,
                               SessionResetService sessionResetService,
                               BookingRepository bookingRepo,
                               PromoCodeRepository promoRepo,
                               PromoCodeBikeRepository promoBikeRepo) {
        this.userRepo = userRepo;
        this.bikeRepo = bikeRepo;
        this.chatSessionRepo = chatSessionRepo;
        this.bookingRepo = bookingRepo;
        this.sessionResetService = sessionResetService;
        this.promoRepo = promoRepo;
        this.promoBikeRepo = promoBikeRepo;
    }

    @Autowired(required = false)
    public void setCacheManager(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    // Use explicit cache access to avoid self-invocation cache issues in unit tests
    @SuppressWarnings("unchecked")
    public List<Bike> getAvailableBikes() {
        if (cacheManager != null) {
            Cache cache = cacheManager.getCache("bikes");
            if (cache != null) {
                List<?> cachedRaw = cache.get("all", List.class);
                if (cachedRaw != null) return (List<Bike>) cachedRaw;
                List<Bike> fresh = bikeRepo.findByIsAvailableTrue();
                cache.put("all", fresh);
                return fresh;
            }
        }
        return bikeRepo.findByIsAvailableTrue();
    }

    public PromoCode getPromoByCode(String code) {
        if (code == null) return null;
        String key = code.toLowerCase(Locale.ENGLISH);
        if (cacheManager != null) {
            Cache cache = cacheManager.getCache("promos");
            if (cache != null) {
                PromoCode p = cache.get(key, PromoCode.class);
                if (p != null) return p;
                PromoCode fresh = promoRepo.findByCodeIgnoreCase(code).orElse(null);
                if (fresh != null) cache.put(key, fresh);
                return fresh;
            }
        }
        return promoRepo.findByCodeIgnoreCase(code).orElse(null);
    }

    public String handleMessage(String from, String text) {
        text = text == null ? "" : text.trim();
        log.info("📨 Message from {}: '{}'", from, text);

        User user = userRepo.findByPhoneNumber(from)
                .orElseGet(() -> {
                    User u = new User();
                    u.setPhoneNumber(from);
                    u.setStage("START");
                    User saved = userRepo.save(u);
                    // ensure we have a usable user even if save() returns null in tests
                    return saved == null ? u : saved;
                });

        ChatSessionEntity session = chatSessionRepo.findByWaId(from).orElse(null);
        if (session == null) {
            ChatSessionEntity candidate = new ChatSessionEntity(from, "START", new HashMap<>());
            ChatSessionEntity saved = null;
            try {
                saved = chatSessionRepo.save(candidate);
            } catch (Exception e) {
                log.warn("⚠️ Could not persist new session for {}: {}", from, e.getMessage());
            }
            session = saved == null ? candidate : saved;
        }

        Map<String, Object> sessionData = readSessionData(session);
        // sessionData is guaranteed non-null from readSessionData
        String origStage = user.getStage() == null ? "START" : user.getStage();
        // Global guard: if user typed 'no' and either the user's stage or session state indicate promo flow,
        // treat it as skipping promo. This covers timing/race differences between session and user stage.
        if ("no".equalsIgnoreCase(text)) {
            String sessState = session == null ? null : session.getState();
            if ("ASK_PROMO".equalsIgnoreCase(origStage) || "ASK_PROMO".equalsIgnoreCase(sessState)) {
                save(user, session, "CONFIRM_BIKE", sessionData);
                return "No promo applied. Please confirm your booking: 1️⃣ Yes  2️⃣ No";
            }
        }
        // If user explicitly replies 'no' while in ASK_PROMO, immediately treat it as 'skip promo'.
        // (handled above globally)
        String stage = origStage;

        // If user explicitly replied 'no' while in ASK_PROMO, honor that and don't override stage.
        boolean userExplicitNo = "ASK_PROMO".equals(origStage) && text.equalsIgnoreCase("no");

        // Only auto-advance to CONFIRM_BIKE if session already contains a promo selection and the user
        // is in a promo/confirm related stage. This prevents accidental jumps when user reselects bikes.
        if (!userExplicitNo && (sessionData.containsKey("promoCodeId") || sessionData.containsKey("promoCode"))
                && ("ASK_PROMO".equals(origStage) || "CONFIRM_BIKE".equals(origStage))) {
            stage = "CONFIRM_BIKE";
        } else if ("ASK_PROMO".equals(stage) && ("1".equals(text) || "2".equals(text))) {
            // user directly replied 1/2 while in ASK_PROMO -> treat as confirmation or reselect
            stage = "CONFIRM_BIKE";
        }

        if (!"BOOKING_CONFIRMED".equals(stage)) {
            if (text.equalsIgnoreCase("cancel")) {
                sessionResetService.resetUserAndSession(from);
                return "❌ Your booking has been cancelled.\nYou can start a new one anytime by typing *Hi* 👋";
            }
        }

        log.info("➡️ Stage: {}", stage);

        switch (stage) {
            case "START":
                user.setStage("ASK_NAME");
                save(user, session, "ASK_NAME", sessionData);
                return String.format("👋 Welcome to *%s*!\nPlease tell me your *name* to start your booking.", displayName);

            case "ASK_NAME":
                if (text.equalsIgnoreCase("hi") || text.equalsIgnoreCase("hello")) {
                    return "😊 Please tell me your *name* to continue.";
                }
                user.setName(text);
                save(user, session, "ASK_DAYS", sessionData);
                return String.format(
                        "Thanks, %s! 🙏\nHow many *days* would you like to rent the bike? (Enter a number)\n\n💡 Tip: Type 'cancel' anytime to reset or start over.",
                        text);

            case "ASK_DAYS":
                try {
                    int days = Integer.parseInt(text);
                    if (days <= 0) throw new NumberFormatException();
                    user.setDays(days);
                    save(user, session, "ASK_START_DATE", sessionData);
                    return "Got it! 📅 Please enter your *rental start date*.\n(You can type 'today' or a date like '25 Oct' or '2025-10-25')";
                } catch (NumberFormatException e) {
                    return "❌ Please enter a valid number of days.";
                }

            case "ASK_START_DATE":
                LocalDate startDate = parseDate(text);
                if (startDate == null) {
                    return "⚠️ Please enter a valid date (e.g., 'today', '25 Oct', or '2025-10-25').";
                }
                user.setStartDate(startDate);
                save(user, session, "ASK_PICKUP", sessionData);
                return "How would you like to pick up your bike?\n1️⃣ Pickup at shop\n2️⃣ Home delivery";

            case "ASK_PICKUP":
                if ("1".equals(text)) {
                    user.setPickupType("Pickup at shop");
                    save(user, session, "ASK_BIKE", sessionData);
                } else if ("2".equals(text)) {
                    user.setPickupType("Home delivery");
                    save(user, session, "ASK_ADDRESS", sessionData);
                    return "Since you selected *home delivery*, please enter your *delivery address*.\n(If unsure, just type your nearest town name.)";
                } else {
                    String lower = text.toLowerCase(Locale.ENGLISH);
                    if (lower.contains("pickup") || lower.contains("shop") || lower.contains("store")) {
                        user.setPickupType("Pickup at shop");
                        save(user, session, "ASK_BIKE", sessionData);
                        return "Okay, pickup at shop noted. Proceeding to bike selection.";
                    } else if (lower.contains("deliver") || lower.contains("home") || lower.contains("door")) {
                        user.setPickupType("Home delivery");
                        save(user, session, "ASK_ADDRESS", sessionData);
                        return "Home delivery noted. Please enter your delivery address.";
                    }
                    return "❌ Please reply with 1 or 2 to choose pickup method.";
                }
                // fallthrough

            case "ASK_ADDRESS":
                if ("ASK_ADDRESS".equals(stage)) {
                    user.setDeliveryAddress(text);
                    save(user, session, "ASK_BIKE", sessionData);
                }

            case "ASK_BIKE":
                List<Bike> availableBikes = getAvailableBikes();
                if (availableBikes.isEmpty()) return "⚠️ Sorry, no bikes are available now.";

                Object bikeMapObj = sessionData.get("bikeMap");
                Map<String, Object> bikeMapRaw = null;
                if (bikeMapObj instanceof Map<?, ?>) {
                    Map<?, ?> tmp = (Map<?, ?>) bikeMapObj;
                    bikeMapRaw = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> en : tmp.entrySet()) {
                        if (en.getKey() instanceof String) {
                            bikeMapRaw.put((String) en.getKey(), en.getValue());
                        }
                    }
                }

                if (bikeMapRaw == null) {
                    bikeMapRaw = new LinkedHashMap<>();
                    StringBuilder list = new StringBuilder("🏍️ Available bikes:\n\n");
                    for (int i = 0; i < availableBikes.size(); i++) {
                        Bike b = availableBikes.get(i);
                        String id = String.valueOf(i + 1);
                        bikeMapRaw.put(id, b.getId());
                        list.append(id).append(". ").append(b.getName())
                                .append(" - Rs.").append(b.getPricePerDay()).append("/day\n");
                    }
                    sessionData.put("bikeMap", bikeMapRaw);
                    saveSession(session, "ASK_BIKE", sessionData);
                    return list + "\nPlease reply with the *bike number* to continue.";
                }

                Map<String, Long> bikeMap = new LinkedHashMap<>();
                for (Map.Entry<String, Object> entry : bikeMapRaw.entrySet()) {
                    bikeMap.put(entry.getKey(), ((Number) entry.getValue()).longValue());
                }

                if (!bikeMap.containsKey(text)) {
                    String lower = text.toLowerCase(Locale.ENGLISH);
                    for (Map.Entry<String, Long> e : bikeMap.entrySet()) {
                        Bike b = bikeRepo.findById(e.getValue()).orElse(null);
                        if (b == null) continue;
                        String nameLower = b.getName() == null ? "" : b.getName().toLowerCase(Locale.ENGLISH);
                        if (nameLower.equals(lower) || nameLower.contains(lower) || lower.contains(nameLower)) {
                            user.setSelectedBikeId(b.getId());
                            // clear promo to avoid accidental auto-apply from previous session
                            if (sessionData != null) {
                                sessionData.remove("promoCodeId");
                                sessionData.remove("promoCode");
                                sessionData.remove("promoAppliedDiscount");
                                sessionData.remove("promoFinalPrice");
                            }
                            save(user, session, "CONFIRM_BIKE", sessionData);

                            LocalDate endDate = user.getStartDate().plusDays(user.getDays());
                            double total = b.getPricePerDay() * user.getDays();
                            double deposit = b.getDeposit();
                            String pickupMsg = user.getPickupType().equals("Pickup at shop")
                                    ? "\n🏠 Shop address: *" + shopAddress + "*"
                                    : "";

                            return String.format(
                                    "You selected *%s* for %d days (%s → %s).\nTotal: Rs%.2f + deposit Rs%.2f\n\nConfirm booking?\n1️⃣ Yes\n2️⃣ No%s",
                                    b.getName(),
                                    user.getDays(),
                                    dateFormatter.format(user.getStartDate()),
                                    dateFormatter.format(endDate),
                                    total, deposit, pickupMsg);
                        }
                    }
                    return "❌ Invalid bike number. Please choose again.";
                }

                Long selectedBikeId = bikeMap.get(text);
                Bike selectedBike = bikeRepo.findById(selectedBikeId).orElse(null);
                if (selectedBike == null) return "❌ Invalid selection. Please choose again.";

                user.setSelectedBikeId(selectedBikeId);
                // clear any existing promo from session so it won't be auto-applied
                if (sessionData != null) {
                    sessionData.remove("promoCodeId");
                    sessionData.remove("promoCode");
                    sessionData.remove("promoAppliedDiscount");
                    sessionData.remove("promoFinalPrice");
                }
                save(user, session, "ASK_PROMO", sessionData);

                LocalDate endDate = user.getStartDate().plusDays(user.getDays());
                double total = selectedBike.getPricePerDay() * user.getDays();
                double deposit = selectedBike.getDeposit();
                String pickupMsg = user.getPickupType().equals("Pickup at shop")
                        ? "\n🏠 Shop address: *" + shopAddress + "*"
                        : "";

                return String.format(
                        "You selected *%s* for %d days (%s → %s).\nTotal: Rs%.2f + deposit Rs%.2f\n\nDo you have a promo code? If yes, type it now to apply it; or reply '1' to confirm the booking, '2' to choose another bike.\n\nConfirm booking?\n1️⃣ Yes\n2️⃣ No%s",
                        selectedBike.getName(),
                        user.getDays(),
                        dateFormatter.format(user.getStartDate()),
                        dateFormatter.format(endDate),
                        total, deposit, pickupMsg);

            case "ASK_PROMO":
                // If user explicitly says 'no' here (safety net), skip promo
                if (text.equalsIgnoreCase("no")) {
                    save(user, session, "CONFIRM_BIKE", sessionData);
                    return "No promo applied. Please confirm your booking: 1️⃣ Yes  2️⃣ No";
                }
                 // New behavior: user may either enter a promo code, or reply '1' to confirm, or '2' to choose another bike.
                 if ("1".equals(text) || "2".equals(text)) {
                     return handleConfirmOrReselect(user, session, sessionData, text);
                 }

                // If empty input, repeat the prompt without requiring a 'no' response
                if (text.isBlank()) {
                    save(user, session, "ASK_PROMO", sessionData);
                    return "Do you have a promo code? If yes, type it now to apply it; or reply '1' to confirm the booking, '2' to choose another bike.";
                }

                PromoCode promoCodeCandidate = getPromoByCode(text);
                if (promoCodeCandidate == null || !Boolean.TRUE.equals(promoCodeCandidate.getActive())) {
                    return "⚠️ Promo code not found or inactive. Please try another code, reply '1' to confirm, or '2' to reselect the bike.";
                }
                int totalAllocSafe = promoCodeCandidate.getTotalAllocation() == null ? 0 : promoCodeCandidate.getTotalAllocation();
                int usedCountSafe = promoCodeCandidate.getUsedCount() == null ? 0 : promoCodeCandidate.getUsedCount();
                if (totalAllocSafe > 0 && usedCountSafe >= totalAllocSafe) {
                    return "⚠️ This promo code has been fully used. Please try another code, reply '1' to confirm, or '2' to reselect the bike.";
                }

                // store promo choice in session (computed discount will be stored after we calculate it)
                sessionData.put("promoCodeId", promoCodeCandidate.getId());
                sessionData.put("promoCode", promoCodeCandidate.getCode());

                // compute discount details to show user
                Bike chosenBike = null;
                if (user.getSelectedBikeId() != null) {
                    chosenBike = bikeRepo.findById(user.getSelectedBikeId()).orElse(null);
                }

                int basePricePromo = 0;
                int depositAmtPromo = 0;
                if (chosenBike != null && user.getDays() != null) {
                    basePricePromo = chosenBike.getPricePerDay() * user.getDays();
                    depositAmtPromo = chosenBike.getDeposit();
                }

                // Determine discount: percentage takes precedence
                int discountPromo = 0;
                int finalPricePromo = basePricePromo;
                if (promoCodeCandidate.getDiscountPercent() != null && promoCodeCandidate.getDiscountPercent() > 0) {
                    discountPromo = (int) Math.round(basePricePromo * (promoCodeCandidate.getDiscountPercent() / 100.0));
                    finalPricePromo = Math.max(0, basePricePromo - discountPromo);
                } else {
                    discountPromo = promoCodeCandidate.getDiscountAmount() == null ? 0 : promoCodeCandidate.getDiscountAmount();
                    finalPricePromo = Math.max(0, basePricePromo - discountPromo);
                }

                // advance user's stage to confirmation (persist user.stage as well)
                // store computed discount and final price in session to ensure the value
                // shown to the user is the value persisted at confirmation (avoids recompute drift)
                sessionData.put("promoAppliedDiscount", discountPromo);
                sessionData.put("promoFinalPrice", finalPricePromo);
                save(user, session, "CONFIRM_BIKE", sessionData);

                String priceLine = "";
                if (chosenBike != null) {
                    priceLine = String.format("Discount: Rs%d. New total: Rs%d + deposit Rs%d\n\n", discountPromo, finalPricePromo, depositAmtPromo);
                }

                return String.format("✅ Promo '%s' applied. %sPlease reply '1' to confirm or '2' to choose another bike.", promoCodeCandidate.getCode(), priceLine);

            case "CONFIRM_BIKE":
                return handleConfirmOrReselect(user, session, sessionData, text);

            case "BOOKING_CONFIRMED":
                if (text.equalsIgnoreCase("cancel")) {
                    save(user, session, "CANCEL_CONFIRM", sessionData);
                    return "⚠️ Are you sure you want to cancel your confirmed booking?\n\n" +
                            "1️⃣ Yes, cancel my booking\n" +
                            "2️⃣ No, keep it active";
                }
                return "✅ Your booking is confirmed! Our team will contact you soon.\n" +
                        "💡 You can type *cancel* if you really wish to cancel this booking.";

            case "CANCEL_CONFIRM":
                if ("1".equalsIgnoreCase(text)) {
                    Booking bookingToCancel = bookingRepo.findTopByWaIdOrderByCreatedAtDesc(user.getPhoneNumber())
                            .orElse(null);

                    if (bookingToCancel != null && !"CANCELLED".equalsIgnoreCase(bookingToCancel.getStatus())) {
                        bookingToCancel.setStatus("CANCELLED");
                        bookingToCancel.setCancelledAt(Instant.now());
                        bookingRepo.save(bookingToCancel);

                        log.info("❌ Booking cancelled for user {}", user.getPhoneNumber());
                    }

                    sessionResetService.resetUserAndSession(user.getPhoneNumber());
                    return "✅ Your booking has been cancelled.\nYou can start a new one anytime by typing *Hi* 👋";
                } else if ("2".equalsIgnoreCase(text)) {
                    save(user, session, "BOOKING_CONFIRMED", sessionData);
                    return "✅ Your booking remains active.\nWe’ll contact you soon for pickup or delivery.";
                }
                return "❌ Please reply 1️⃣ to cancel or 2️⃣ to keep your booking.";

            default:
                return "🤖 Sorry, I didn't understand that. Please type *Hi* to start a booking or follow the prompts.";
        }
    }

    private LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) return null;

        text = text.toLowerCase().trim();
        if (text.equals("today")) return LocalDate.now();
        text = text.replaceAll("(\\d+)(st|nd|rd|th)", "$1");

        LocalDate strictDate = tryStrictPatterns(text);
        if (strictDate != null) return strictDate;

        try {
            Parser parser = new Parser();
            List<com.joestelmach.natty.DateGroup> groups = parser.parse(text);
            if (!groups.isEmpty()) {
                Date date = groups.get(0).getDates().get(0);
                return Instant.ofEpochMilli(date.getTime())
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();
            }
        } catch (Exception e) {
            log.warn("⚠️ Natty failed to parse '{}': {}", text, e.getMessage());
        }

        log.warn("⚠️ Could not parse date '{}'", text);
        return null;
    }

    private LocalDate tryStrictPatterns(String text) {
        DateTimeFormatter[] formatters = new DateTimeFormatter[]{
                DateTimeFormatter.ofPattern("yyyy-M-d"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                DateTimeFormatter.ofPattern("d MMM yyyy"),
                DateTimeFormatter.ofPattern("MMM d yyyy"),
                DateTimeFormatter.ofPattern("d MMM"),
                DateTimeFormatter.ofPattern("MMM d")
        };

        boolean appendYear = !text.matches(".*\\d{4}.*");

        for (DateTimeFormatter formatter : formatters) {
            try {
                String parseText = text;
                if (appendYear) parseText += " " + Year.now().getValue();
                return LocalDate.parse(parseText, formatter.withLocale(Locale.ENGLISH));
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private void save(User user, ChatSessionEntity session, String nextState, Map<String, Object> sessionData) {
        user.setStage(nextState);
        userRepo.save(user);
        saveSession(session, nextState, sessionData);
    }

    private void saveSession(ChatSessionEntity session, String nextState, Map<String, Object> sessionData) {
        try {
            if (session == null) {
                // create a minimal placeholder so we don't get NPEs in tests where repository mocks return null
                log.warn("⚠️ saveSession called with null session - creating placeholder session");
                session = new ChatSessionEntity("unknown", nextState, sessionData == null ? new HashMap<>() : sessionData);
            }
            session.setState(nextState);
            session.setDataJson(sessionData);
            session.setLastUpdated(Instant.now());
            chatSessionRepo.save(session);
        } catch (Exception e) {
            log.error("⚠️ Failed to save session: {}", e.getMessage());
        }
    }

    private Map<String, Object> readSessionData(ChatSessionEntity session) {
        try {
            if (session == null) return new HashMap<>();
            if (session.getDataJson() == null) return new HashMap<>();
            return session.getDataJson();
        } catch (Exception e) {
            log.error("⚠️ Failed to parse session JSON", e);
            return new HashMap<>();
        }
    }

    // Helper to centralize confirm (1) and reselect (2) logic.
    private String handleConfirmOrReselect(User user, ChatSessionEntity session, Map<String, Object> sessionData, String text) {
        // Confirm booking path
        if ("1".equalsIgnoreCase(text)) {
            Bike selectedBike = null;
            if (user.getSelectedBikeId() != null) {
                selectedBike = bikeRepo.findById(user.getSelectedBikeId()).orElse(null);
            }
            if (selectedBike == null) {
                return "⚠️ Something went wrong saving your booking. Please try again.";
            }

            LocalDate endDate = user.getStartDate().plusDays(user.getDays());
            Booking booking = new Booking();
            booking.setWaId(user.getPhoneNumber());
            booking.setName(user.getName());
            booking.setBike(selectedBike.getName());
            booking.setDuration(user.getDays());

            int basePrice = selectedBike.getPricePerDay() * user.getDays();
            booking.setPrice(basePrice);
            booking.setDeposit(selectedBike.getDeposit());

            // Attempt to apply promo if present in session
            if (sessionData != null && (sessionData.containsKey("promoCodeId") || sessionData.containsKey("promoCode"))) {
                PromoCode p = null;
                // try by id (could be Number or String)
                if (sessionData.containsKey("promoCodeId")) {
                    Object pidObj = sessionData.get("promoCodeId");
                    Long pid = null;
                    if (pidObj instanceof Number) {
                        pid = ((Number) pidObj).longValue();
                    } else if (pidObj instanceof String) {
                        try { pid = Long.valueOf((String) pidObj); } catch (NumberFormatException ignored) {}
                    }
                    if (pid != null) p = promoRepo.findById(pid).orElse(null);
                }
                // fallback to code lookup
                if (p == null && sessionData.containsKey("promoCode")) {
                    String code = String.valueOf(sessionData.get("promoCode"));
                    p = promoRepo.findByCodeIgnoreCase(code).orElse(null);
                }

                if (p != null && Boolean.TRUE.equals(p.getActive())) {
                    // check bike-specific mapping
                    boolean applicable = true;
                    if (p.getId() != null) {
                        var mappings = promoBikeRepo.findByPromoCode_Id(p.getId());
                        if (mappings != null && !mappings.isEmpty()) {
                            Long selBikeId = selectedBike.getId();
                            applicable = mappings.stream().anyMatch(m -> m.getBike() != null && Objects.equals(m.getBike().getId(), selBikeId));
                        }
                    }

                    if (applicable) {
                        // Prefer stored session values to avoid drift
                        Integer appliedDiscount = null;
                        Integer appliedFinal = null;
                        if (sessionData.containsKey("promoAppliedDiscount") && sessionData.containsKey("promoFinalPrice")) {
                            try {
                                Object discObj = sessionData.get("promoAppliedDiscount");
                                Object finalObj = sessionData.get("promoFinalPrice");
                                if (discObj instanceof Number) appliedDiscount = ((Number) discObj).intValue();
                                else appliedDiscount = Integer.valueOf(String.valueOf(discObj));
                                if (finalObj instanceof Number) appliedFinal = ((Number) finalObj).intValue();
                                else appliedFinal = Integer.valueOf(String.valueOf(finalObj));
                            } catch (Exception ignored) {
                                appliedDiscount = null; appliedFinal = null;
                            }
                        }

                        // Recompute if session values missing
                        if (appliedDiscount == null) {
                            if (p.getDiscountPercent() != null && p.getDiscountPercent() > 0) {
                                appliedDiscount = (int) Math.round(basePrice * (p.getDiscountPercent() / 100.0));
                            } else {
                                appliedDiscount = p.getDiscountAmount() == null ? 0 : p.getDiscountAmount();
                            }
                            appliedFinal = Math.max(0, basePrice - (appliedDiscount == null ? 0 : appliedDiscount));
                        }

                        booking.setPrice(appliedFinal);
                        booking.setPromoCode(p);
                        booking.setPromoApplied(Boolean.TRUE);
                        booking.setPromoDiscountAmount(appliedDiscount);

                        // increment usage and persist promo
                        p.setUsedCount((p.getUsedCount() == null ? 0 : p.getUsedCount()) + 1);
                        promoRepo.save(p);
                    } else {
                        log.info("➡️ Promo {} not applicable to bike id {} - ignored", p.getCode(), selectedBike.getId());
                    }
                }
            }

            booking.setStatus("CONFIRMED");
            booking.setStartDate(user.getStartDate());
            booking.setEndDate(endDate);
            booking.setPickupType(user.getPickupType());
            booking.setDeliveryAddress(user.getDeliveryAddress());
            booking.setCreatedAt(Instant.now());
            bookingRepo.save(booking);

            log.info("🧾 Booking CONFIRMED and saved for {}", user.getPhoneNumber());

            // Clear promo entries from session after successful booking
            if (sessionData != null) {
                sessionData.remove("promoCodeId");
                sessionData.remove("promoCode");
                sessionData.remove("promoAppliedDiscount");
                sessionData.remove("promoFinalPrice");
                log.info("➡️ Cleared promo from session for user {} after booking confirmation", user.getPhoneNumber());
            }

            // persist updated user stage and cleared session
            save(user, session, "BOOKING_CONFIRMED", sessionData);

            String pickupMsg = user.getPickupType().equals("Pickup at shop") ? "\n🏠 Pickup location: *" + shopAddress + "*" : "";
            double total = booking.getPrice();
            double deposit = booking.getDeposit();

            String promoMsg = "";
            if (booking.getPromoApplied() != null && booking.getPromoApplied() && booking.getPromoCode() != null) {
                int applied = booking.getPromoDiscountAmount() == null ? 0 : booking.getPromoDiscountAmount();
                promoMsg = "\n\nPromo applied: " + booking.getPromoCode().getCode() + " - Rs" + applied +
                        String.format("\nNew total: Rs%.2f + deposit Rs%.2f", (double) booking.getPrice(), (double) deposit);
            }

            return String.format(
                    "✅ Booking confirmed, %s! 🎉\n" +
                            "📅 *%s → %s*\n🏍️ *%s*\n💰 Rs%.2f + deposit Rs%.2f%s\n\n" +
                            "Please prepare your documents for verification:\n" +
                            "📸 Driver’s or international license\n📄 Passport & visa QR\n\n" +
                            "Our team will contact you soon to finalize pickup or delivery.%s\n\n" +
                            "💡 You can type *cancel* anytime to cancel your booking.",
                    user.getName(),
                    dateFormatter.format(user.getStartDate()),
                    dateFormatter.format(endDate),
                    selectedBike.getName(),
                    total,
                    deposit,
                    promoMsg,
                    pickupMsg
            );
        }

        // Reselect path - user wants to choose another bike
        if ("2".equalsIgnoreCase(text)) {
            if (sessionData != null) {
                sessionData.remove("promoCodeId");
                sessionData.remove("promoCode");
                sessionData.remove("promoAppliedDiscount");
                sessionData.remove("promoFinalPrice");
                log.info("➡️ User {} reselected bike — cleared promo from session", user.getPhoneNumber());
            }
            save(user, session, "ASK_BIKE", sessionData);
            return "No problem! Please choose another bike number.";
        }

        return "❌ Please reply 1️⃣ to confirm or 2️⃣ to reselect.";
    }
}
