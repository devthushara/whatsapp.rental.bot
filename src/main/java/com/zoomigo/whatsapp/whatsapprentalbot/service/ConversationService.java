package com.zoomigo.whatsapp.whatsapprentalbot.service;

import com.zoomigo.whatsapp.whatsapprentalbot.entity.Bike;
import com.zoomigo.whatsapp.whatsapprentalbot.entity.Booking;
import com.zoomigo.whatsapp.whatsapprentalbot.entity.ChatSessionEntity;
import com.zoomigo.whatsapp.whatsapprentalbot.entity.User;
import com.zoomigo.whatsapp.whatsapprentalbot.repository.BikeRepository;
import com.zoomigo.whatsapp.whatsapprentalbot.repository.BookingRepository;
import com.zoomigo.whatsapp.whatsapprentalbot.repository.ChatSessionRepository;
import com.zoomigo.whatsapp.whatsapprentalbot.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy");
    @Value("${app.shop-address}")
    private String shopAddress;

    public ConversationService(UserRepository userRepo, BikeRepository bikeRepo,
                               ChatSessionRepository chatSessionRepo,
                               SessionResetService sessionResetService,
                               BookingRepository bookingRepo) {
        this.userRepo = userRepo;
        this.bikeRepo = bikeRepo;
        this.chatSessionRepo = chatSessionRepo;
        this.bookingRepo = bookingRepo;
        this.sessionResetService = sessionResetService;
    }


    public String handleMessage(String from, String text) {
        text = text.trim();
        log.info("📨 Message from {}: '{}'", from, text);

        User user = userRepo.findByPhoneNumber(from)
                .orElseGet(() -> {
                    User u = new User();
                    u.setPhoneNumber(from);
                    u.setStage("START");
                    return userRepo.save(u);
                });

        ChatSessionEntity session = chatSessionRepo.findByWaId(from)
                .orElseGet(() -> chatSessionRepo.save(new ChatSessionEntity(from, "START", new HashMap<>())));

        Map<String, Object> sessionData = readSessionData(session);
        String stage = user.getStage();

        if(!"BOOKING_CONFIRMED".equals(stage)) {
            // 🧨 Kill switch
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
                return "👋 Welcome to *ZoomiGo MotoRent*!\nPlease tell me your *name* to start your booking.";

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
                    return "❌ Please reply with 1 or 2 to choose pickup method.";
                }
                // fallthrough

            case "ASK_ADDRESS":
                if ("ASK_ADDRESS".equals(stage)) {
                    user.setDeliveryAddress(text);
                    save(user, session, "ASK_BIKE", sessionData);
                }

            case "ASK_BIKE":
                List<Bike> availableBikes = bikeRepo.findByIsAvailableTrue();
                if (availableBikes.isEmpty()) return "⚠️ Sorry, no bikes are available now.";

                Map<String, Object> bikeMapRaw = (Map<String, Object>) sessionData.get("bikeMap");
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
                    return "❌ Invalid bike number. Please choose again.";
                }

                Long selectedBikeId = bikeMap.get(text);
                Bike selectedBike = bikeRepo.findById(selectedBikeId).orElse(null);
                if (selectedBike == null) return "❌ Invalid selection. Please choose again.";

                user.setSelectedBikeId(selectedBikeId);
                save(user, session, "CONFIRM_BIKE", sessionData);

                LocalDate endDate = user.getStartDate().plusDays(user.getDays());
                double total = selectedBike.getPricePerDay() * user.getDays();
                double deposit = selectedBike.getDeposit();
                String pickupMsg = user.getPickupType().equals("Pickup at shop")
                        ? "\n🏠 Shop address: *" + shopAddress + "*"
                        : "";

                return String.format(
                        "You selected *%s* for %d days (%s → %s).\nTotal: Rs%.2f + deposit Rs%.2f\n\nConfirm booking?\n1️⃣ Yes\n2️⃣ No%s",
                        selectedBike.getName(),
                        user.getDays(),
                        dateFormatter.format(user.getStartDate()),
                        dateFormatter.format(endDate),
                        total, deposit, pickupMsg);

            case "CONFIRM_BIKE":
                if ("1".equalsIgnoreCase(text)) {
                    // ✅ Confirm booking immediately
                    Bike selectedBike2 = bikeRepo.findById(user.getSelectedBikeId()).orElse(null);
                    if (selectedBike2 != null) {
                        LocalDate endDate2 = user.getStartDate().plusDays(user.getDays());

                        Booking booking = new Booking();
                        booking.setWaId(user.getPhoneNumber());
                        booking.setName(user.getName());
                        booking.setBike(selectedBike2.getName());
                        booking.setDuration(user.getDays());
                        booking.setPrice(selectedBike2.getPricePerDay() * user.getDays());
                        booking.setDeposit(selectedBike2.getDeposit());
                        booking.setStatus("CONFIRMED");
                        booking.setStartDate(user.getStartDate());
                        booking.setEndDate(endDate2);
                        booking.setPickupType(user.getPickupType());
                        booking.setDeliveryAddress(user.getDeliveryAddress());
                        booking.setCreatedAt(Instant.now());
                        bookingRepo.save(booking);

                        log.info("🧾 Booking CONFIRMED and saved for {}", user.getPhoneNumber());

                        save(user, session, "BOOKING_CONFIRMED", sessionData);

                        String pickupMsg2 = user.getPickupType().equals("Pickup at shop")
                                ? "\n🏠 Pickup location: *" + shopAddress + "*"
                                : "";

                        double total2 = selectedBike2.getPricePerDay() * user.getDays();
                        double deposit2 = selectedBike2.getDeposit();

                        return String.format(
                                "✅ Booking confirmed, %s! 🎉\n" +
                                        "📅 *%s → %s*\n🏍️ *%s*\n💰 Rs%.2f + deposit Rs%.2f\n\n" +
                                        "Please prepare your documents for verification:\n" +
                                        "📸 Driver’s or international license\n📄 Passport & visa QR\n\n" +
                                        "Our team will contact you soon to finalize pickup or delivery.%s\n\n" +
                                        "💡 You can type *cancel* anytime to cancel your booking.",
                                user.getName(),
                                dateFormatter.format(user.getStartDate()),
                                dateFormatter.format(endDate2),
                                selectedBike2.getName(),
                                total2,
                                deposit2,
                                pickupMsg2
                        );
                    }
                    return "⚠️ Something went wrong saving your booking. Please try again.";
                } else if ("2".equalsIgnoreCase(text)) {
                    save(user, session, "ASK_BIKE", sessionData);
                    return "No problem! Please choose another bike number.";
                }
                return "❌ Please reply 1️⃣ to confirm or 2️⃣ to reselect.";


// 🔸 BOOKING_CONFIRMED
            case "BOOKING_CONFIRMED":
                if (text.equalsIgnoreCase("cancel")) {
                    save(user, session, "CANCEL_CONFIRM", sessionData);
                    return "⚠️ Are you sure you want to cancel your confirmed booking?\n\n" +
                            "1️⃣ Yes, cancel my booking\n" +
                            "2️⃣ No, keep it active";
                }
                return "✅ Your booking is confirmed! Our team will contact you soon.\n" +
                        "💡 You can type *cancel* if you really wish to cancel this booking.";


// 🔸 CANCEL_CONFIRM
            case "CANCEL_CONFIRM":
                if ("1".equalsIgnoreCase(text)) {
                    Booking bookingToCancel = bookingRepo.findTopByWaIdOrderByCreatedAtDesc(user.getPhoneNumber())
                            .orElse(null);

                    if (bookingToCancel != null && !"CANCELLED".equalsIgnoreCase(bookingToCancel.getStatus())) {
                        bookingToCancel.setStatus("CANCELLED");
                        bookingToCancel.setCancelledAt(Instant.now());
                        bookingRepo.save(bookingToCancel);

                        log.info("❌ Booking cancelled for user {}", user.getPhoneNumber());

                        // ✅ Optional: Notify admin(s)
            /*
            List<String> adminNumbers = List.of("6598765432", "6587654321");
            String alert = String.format(
                "🚨 *Booking Cancelled!*\n👤 %s (%s)\n🏍️ %s\n📅 %s → %s",
                bookingToCancel.getName(), bookingToCancel.getWaId(),
                bookingToCancel.getBike(),
                bookingToCancel.getStartDate(), bookingToCancel.getEndDate()
            );
            adminNumbers.forEach(num -> whatsappService.sendTextMessage(num, alert));
            */
                    }

                    sessionResetService.resetUserAndSession(user.getPhoneNumber());
                    return "✅ Your booking has been cancelled.\nYou can start a new one anytime by typing *Hi* 👋";
                } else if ("2".equalsIgnoreCase(text)) {
                    save(user, session, "BOOKING_CONFIRMED", sessionData);
                    return "✅ Your booking remains active.\nWe’ll contact you soon for pickup or delivery.";
                }
                return "❌ Please reply 1️⃣ to cancel or 2️⃣ to keep your booking.";


            default:
                // Catch-all for any unexpected stage
                return "🤖 Hmm, looks like something got mixed up.\n" +
                        "Please type *Hi* to start a new booking or *cancel* to reset.";

        }
    }

    private LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) return null;

        text = text.toLowerCase().trim();

        // Handle 'today'
        if (text.equals("today")) return LocalDate.now();

        // Remove ordinal suffixes like 1st, 2nd, 25th
        text = text.replaceAll("(\\d+)(st|nd|rd|th)", "$1");

        // First try strict patterns (yyyy-M-d, d MMM, MMM d, etc.)
        LocalDate strictDate = tryStrictPatterns(text);
        if (strictDate != null) return strictDate;

        // If strict parsing fails, try Natty
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
            } catch (Exception ignored) {}
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
            if (session.getDataJson() == null) return new HashMap<>();
            return session.getDataJson();
        } catch (Exception e) {
            log.error("⚠️ Failed to parse session JSON", e);
            return new HashMap<>();
        }
    }
}
