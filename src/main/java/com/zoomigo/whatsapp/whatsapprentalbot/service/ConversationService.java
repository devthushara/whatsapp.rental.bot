package com.zoomigo.whatsapp.whatsapprentalbot.service;

import com.zoomigo.whatsapp.whatsapprentalbot.entity.Bike;
import com.zoomigo.whatsapp.whatsapprentalbot.entity.ChatSessionEntity;
import com.zoomigo.whatsapp.whatsapprentalbot.entity.User;
import com.zoomigo.whatsapp.whatsapprentalbot.repository.BikeRepository;
import com.zoomigo.whatsapp.whatsapprentalbot.repository.ChatSessionRepository;
import com.zoomigo.whatsapp.whatsapprentalbot.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ConversationService {

    private final UserRepository userRepo;
    private final BikeRepository bikeRepo;
    private final ChatSessionRepository chatSessionRepo;
    private final SessionResetService sessionResetService;


    @Value("${app.shop-address}")
    private String shopAddress;

    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy");

    public ConversationService(UserRepository userRepo, BikeRepository bikeRepo,
                               ChatSessionRepository chatSessionRepo,
                               SessionResetService sessionResetService) {
        this.userRepo = userRepo;
        this.bikeRepo = bikeRepo;
        this.chatSessionRepo = chatSessionRepo;
        this.sessionResetService = sessionResetService;
    }


    public String handleMessage(String from, String text) {
        text = text.trim();
        log.info("📨 Message from {}: '{}'", from, text);

        // 🧨 Kill switch
        if (text.equalsIgnoreCase("cancel")) {
            sessionResetService.resetUserAndSession(from);
            return "❌ Your booking has been cancelled.\nYou can start a new one anytime by typing *Hi* 👋";
        }

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
                    save(user, session, "WAITING_DOCUMENTS", sessionData);

                    String pickupMsg2 = user.getPickupType().equals("Pickup at shop")
                            ? "\n🏠 Shop address: *" + shopAddress + "*"
                            : "";

                    return "✅ Great choice!\nPlease prepare the following documents:\n" +
                            "📸 *Driver’s or international license*\n" +
                            "📄 *Passport & visa QR*\n\n" +
                            "Our team will reach out soon to confirm your booking." + pickupMsg2 +
                            "\n\n💡 Tip: Type 'cancel' anytime to cancel or start over.";
                }
                else if ("2".equalsIgnoreCase(text)) {
                    save(user, session, "ASK_BIKE", sessionData);
                    return "No worries! Please choose another bike number.";
                }
                return "❌ Please reply 1️⃣ to confirm or 2️⃣ to reselect.";

            case "WAITING_DOCUMENTS":
                return "📩 Thank you! We’ve received your booking details and documents.\n" +
                        "Our team is reviewing your request and will contact you shortly to confirm your pickup or delivery.\n\n" +
                        "📞 For urgent matters, please call us directly.\n" +
                        "💡 You can also type *cancel* to start a new booking anytime.";

            default:
                // Catch-all for any unexpected stage
                return "🤖 Hmm, looks like something got mixed up.\n" +
                        "Please type *Hi* to start a new booking or *cancel* to reset.";

        }
    }

    private LocalDate parseDate(String text) {
        text = text.toLowerCase();
        if (text.equals("today")) return LocalDate.now();
        try {
            if (text.matches("\\d{4}-\\d{2}-\\d{2}"))
                return LocalDate.parse(text);
            return LocalDate.parse(text + " " + Year.now(), DateTimeFormatter.ofPattern("d MMM yyyy"));
        } catch (Exception e) {
            log.warn("⚠️ error occurred while parsing the date: {}", e.getMessage());
            return null;
        }
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
