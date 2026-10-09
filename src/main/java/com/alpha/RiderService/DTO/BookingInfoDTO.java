package com.alpha.RiderService.DTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// The Rider service keeps its own copy of this class. The two services never share classes,
// they only agree on the JSON field names.
@JsonIgnoreProperties(ignoreUnknown = true)
public class BookingInfoDTO {

	private Long bookingId;
	private Long customerId;
	private Long riderId;
	private String status;
	private String vehicleType;
	private String pickupLocation;
	private String dropLocation;
	private double pickupLat;
	private double pickupLon;
	private double dropLat;
	private double dropLon;
	private double fare;
	private double distanceKm;

	public BookingInfoDTO() {
		super();
	}

	public Long getBookingId() {
		return bookingId;
	}

	public void setBookingId(Long bookingId) {
		this.bookingId = bookingId;
	}

	public Long getCustomerId() {
		return customerId;
	}

	public void setCustomerId(Long customerId) {
		this.customerId = customerId;
	}

	public Long getRiderId() {
		return riderId;
	}

	public void setRiderId(Long riderId) {
		this.riderId = riderId;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getVehicleType() {
		return vehicleType;
	}

	public void setVehicleType(String vehicleType) {
		this.vehicleType = vehicleType;
	}

	public String getPickupLocation() {
		return pickupLocation;
	}

	public void setPickupLocation(String pickupLocation) {
		this.pickupLocation = pickupLocation;
	}

	public String getDropLocation() {
		return dropLocation;
	}

	public void setDropLocation(String dropLocation) {
		this.dropLocation = dropLocation;
	}

	public double getPickupLat() {
		return pickupLat;
	}

	public void setPickupLat(double pickupLat) {
		this.pickupLat = pickupLat;
	}

	public double getPickupLon() {
		return pickupLon;
	}

	public void setPickupLon(double pickupLon) {
		this.pickupLon = pickupLon;
	}

	public double getDropLat() {
		return dropLat;
	}

	public void setDropLat(double dropLat) {
		this.dropLat = dropLat;
	}

	public double getDropLon() {
		return dropLon;
	}

	public void setDropLon(double dropLon) {
		this.dropLon = dropLon;
	}

	public double getFare() {
		return fare;
	}

	public void setFare(double fare) {
		this.fare = fare;
	}

	public double getDistanceKm() {
		return distanceKm;
	}

	public void setDistanceKm(double distanceKm) {
		this.distanceKm = distanceKm;
	}
}