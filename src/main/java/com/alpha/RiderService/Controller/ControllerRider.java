package com.alpha.RiderService.Controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.alpha.RiderService.DTO.BookingInfoDTO;
import com.alpha.RiderService.DTO.CreateRiderDTO;
import com.alpha.RiderService.DTO.ResponseStructure;
import com.alpha.RiderService.Entity.Coordinate;
import com.alpha.RiderService.Entity.Rider;
import com.alpha.RiderService.Service.ServiceRider;

@RestController
@RequestMapping("/rider")
public class ControllerRider {

	@Autowired
	ServiceRider serviceRider;

	@PostMapping("/createaccount")
	public ResponseStructure<Rider> createaccount(@RequestBody CreateRiderDTO createrider) {
		return serviceRider.createaccount(createrider);
	}

	@PostMapping("/sendcurrentlocation")
	public ResponseStructure<String> sendcurrentlocation(@RequestBody Coordinate coordinate, @RequestParam int id,
			@RequestParam String vechileType) {
		return serviceRider.sendcurrentlocation(coordinate, id, vechileType);
	}

	@GetMapping("/assignedrides")
	public ResponseStructure<List<Map<String, String>>> getassignedrides(@RequestParam Long riderId) {
		return serviceRider.getassignedrides(riderId);
	}

	@PutMapping("/acceptride/{riderId}/{bookingId}")
	public ResponseStructure<String> acceptride(@PathVariable Long riderId, @PathVariable Long bookingId) {
		return serviceRider.acceptride(riderId, bookingId);
	}

	@GetMapping("/movetowardspickup")
	public ResponseStructure<Map<String, String>> movetowardspickup(@RequestParam Long riderId,
			@RequestParam Long bookingId) {
		return serviceRider.movetowardspickup(riderId, bookingId);
	}

	@GetMapping("/movetowardsdrop")
	public ResponseStructure<Map<String, String>> movetowardsdrop(@RequestParam Long riderId,
			@RequestParam Long bookingId) {
		return serviceRider.movetowardsdrop(riderId, bookingId);
	}

	@PutMapping("/updatearrival")
	public ResponseStructure<String> updatearrival(@RequestParam Long riderId, @RequestParam Long bookingId) {
		return serviceRider.updatearrival(riderId, bookingId);
	}

	@PutMapping("/verifyotp")
	public ResponseStructure<String> verifyotp(@RequestParam String otp, @RequestParam Long riderId,
			@RequestParam Long bookingId) {
		return serviceRider.verifyotp(otp, riderId, bookingId);
	}

	@PutMapping("/completeride")
	public ResponseStructure<Map<String, Object>> completeride(@RequestParam Long riderId,
			@RequestParam Long bookingId) {
		return serviceRider.completeride(riderId, bookingId);
	}

	@PutMapping("/goinactive")
	public ResponseStructure<String> goinactive(@RequestParam Long riderId) {
		return serviceRider.goinactive(riderId);
	}

	@GetMapping("/ridehistory")
	public ResponseStructure<List<BookingInfoDTO>> ridehistory(@RequestParam Long riderId,
			@RequestParam(defaultValue = "all") String status) {
		return serviceRider.ridehistory(riderId, status);
	}

	@GetMapping("/earning/summary")
	public ResponseStructure<Map<String, Object>> earningsummary(@RequestParam Long riderId,
			@RequestParam(required = false) String from, @RequestParam(required = false) String to) {
		return serviceRider.earningsummary(riderId, from, to);
	}
}