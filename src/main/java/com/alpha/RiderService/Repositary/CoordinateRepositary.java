package com.alpha.RiderService.Repositary;

import org.springframework.data.jpa.repository.JpaRepository;

import com.alpha.RiderService.Entity.Coordinate;

public interface CoordinateRepositary extends JpaRepository<Coordinate, Long>{

}
