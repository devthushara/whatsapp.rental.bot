package com.zoomigo.whatsapp.whatsapprentalbot.util;

import com.zoomigo.whatsapp.whatsapprentalbot.entity.Booking;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BookingPromptBuilderTest {

    @Test
    void buildPrompt_includesMessageAndBookingInfo_whenBookingPresent() {
        BookingPromptBuilder b = new BookingPromptBuilder();
        Booking booking = new Booking();
        booking.setName("Alice");
        booking.setBike("Honda Dio");
        String prompt = b.buildPrompt("I want a bike", booking);

        assertThat(prompt).contains("User message: \"I want a bike\"");
        assertThat(prompt).contains("Current booking info: ");
        assertThat(prompt).contains("Alice");
        assertThat(prompt).contains("Honda Dio");
    }

    @Test
    void buildPrompt_noBookingJustContainsMessage() {
        BookingPromptBuilder b = new BookingPromptBuilder();
        String prompt = b.buildPrompt("Hello world", null);

        assertThat(prompt).contains("User message: \"Hello world\"");
        assertThat(prompt).doesNotContain("Current booking info:");
    }
}

