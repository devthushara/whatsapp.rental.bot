package com.zoomigo.whatsapp.whatsapprentalbot.util;

import com.zoomigo.whatsapp.whatsapprentalbot.entity.Booking;
import org.springframework.stereotype.Component;

@Component
public class BookingPromptBuilder {

    public String buildPrompt(String message, Booking currentBooking) {
        String base = """
                    You are an assistant helping to handle motorcycle rental bookings.
                    Extract or infer the following details from the message:
                    - Name
                    - Number of rental days
                    - Start date
                    - Pickup method (shop/home)
                    - Address (if provided)
                    - Selected bike name
                    Respond in JSON only, like:
                    {
                      "name": "...",
                      "days": 0,
                      "startDate": "...",
                      "pickupMethod": "shop/home",
                      "address": "...",
                      "bike": "..."
                    }
                    If some fields are missing, leave them null.
                    User message: "%s"
                """.formatted(message);

        if (currentBooking != null) {
            base += "\nCurrent booking info: " + currentBooking.toString();
        }

        return base;
    }
}
