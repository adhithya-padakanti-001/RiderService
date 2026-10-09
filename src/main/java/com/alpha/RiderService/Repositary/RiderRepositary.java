package com.alpha.RiderService.Repositary;

import org.springframework.data.jpa.repository.JpaRepository;

import com.alpha.RiderService.Entity.Rider;

public interface RiderRepositary extends JpaRepository<Rider, Long>{

}
