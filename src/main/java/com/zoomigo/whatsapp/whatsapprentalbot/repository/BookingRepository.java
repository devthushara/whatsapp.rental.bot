package com.zoomigo.whatsapp.whatsapprentalbot.repository;

import com.zoomigo.whatsapp.whatsapprentalbot.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, Long> {
}