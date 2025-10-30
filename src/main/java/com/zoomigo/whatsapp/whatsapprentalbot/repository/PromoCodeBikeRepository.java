package com.zoomigo.whatsapp.whatsapprentalbot.repository;

import com.zoomigo.whatsapp.whatsapprentalbot.entity.PromoCodeBike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PromoCodeBikeRepository extends JpaRepository<PromoCodeBike, Long> {
    List<PromoCodeBike> findByPromoCode_Id(Long promoId);
    List<PromoCodeBike> findByPromoCode_CodeIgnoreCase(String code);
}

