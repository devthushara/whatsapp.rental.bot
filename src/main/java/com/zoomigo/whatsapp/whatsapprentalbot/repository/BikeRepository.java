package com.zoomigo.whatsapp.whatsapprentalbot.repository;

import com.zoomigo.whatsapp.whatsapprentalbot.entity.Bike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BikeRepository extends JpaRepository<Bike, Long> {
    List<Bike> findByIsAvailableTrue();
}
