package com.alpha.RiderService.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.dao.DataAccessException;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.alpha.RiderService.DTO.BookingInfoDTO;
import com.alpha.RiderService.DTO.CreateRiderDTO;
import com.alpha.RiderService.DTO.EarningSummaryDTO;
import com.alpha.RiderService.DTO.ResponseStructure;
import com.alpha.RiderService.Entity.Coordinate;
import com.alpha.RiderService.Entity.Rider;
import com.alpha.RiderService.Repositary.RiderRepositary;

@Service
public class ServiceRider {

	@Autowired
	private StringRedisTemplate stringRedisTemplate;

	private static final String ACTIVE_RIDERS_KEY = "riders:active:";
	private static final String RIDER_ALIVE_KEY = "rider:alive:";
	// riders poll every 30 seconds; 90 seconds = 3 missed polls -> inactive
	private static final Duration ALIVE_TTL = Duration.ofSeconds(90);
	private static final String LIVE_LOCATION_KEY = "rider:location:";

	private static final String RIDER_BLOCKED_KEY = "rider:blocked:";
	private static final double WALLET_MIN = 1000;
	private static final double COMMISSION_RATE = 0.10;

	// Hyderabad service area (same polygon as the Customer service)
	private static final double[][] HYDERABAD_SERVICE_AREA = { { 17.6300, 78.4800 }, { 17.5900, 78.5800 },
			{ 17.4600, 78.6800 }, { 17.3300, 78.6300 }, { 17.2700, 78.5700 }, { 17.2400, 78.4800 },
			{ 17.2300, 78.4000 }, { 17.3000, 78.3300 }, { 17.3900, 78.3000 }, { 17.4800, 78.2600 },
			{ 17.5600, 78.3300 }, { 17.6000, 78.4200 } };

	@Autowired
	RiderRepositary riderRepositary;

	public ResponseStructure<Rider> createaccount(CreateRiderDTO createrider) {

		Rider rider = new Rider();
		rider.setName(createrider.getName());
		rider.setEmail(createrider.getEmail());
		rider.setPhone(createrider.getPhone());
		rider.setGender(createrider.getGender());
		rider.setStatus(createrider.getStatus());
		rider.setDrivinglicence(createrider.getDrivinglicence());
		rider.setVechile(createrider.getVechile());

		riderRepositary.save(rider);

		ResponseStructure<Rider> rs = new ResponseStructure<Rider>();
		rs.setStatuscode(HttpStatus.ACCEPTED.value());
		rs.setData(rider);
		rs.setMessage("The Rider account created Sucessfully");
		return rs;
	}

//============================================================================================================

	public ResponseStructure<String> sendcurrentlocation(Coordinate coordinate, int id, String vechileType) {
		ResponseStructure<String> rs = new ResponseStructure<String>();

		// a blocked rider (wallet below the minimum) is neither tracked nor offered
		// rides
		if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(RIDER_BLOCKED_KEY + id))) {
			rs.setStatuscode(HttpStatus.FORBIDDEN.value());
			rs.setMessage(
					"Your account is blocked because your wallet is below " + (int) WALLET_MIN + ". Please recharge");
			rs.setData(null);
			return rs;
		}

		String vechile_type = vechileType == null ? "" : vechileType.trim().toUpperCase();
		String member = String.valueOf(id);

		try {
			// on a ride? the busy key holds the booking id
			String rideBookingId = stringRedisTemplate.opsForValue().get(RIDER_BUSY_KEY + id);
			boolean onRide = rideBookingId != null;

			// only a rider who wants new rides has to be inside the service area
			if (!onRide && !isInServiceArea(coordinate.getLatitude(), coordinate.getLongitue())) {
				rs.setStatuscode(HttpStatus.BAD_REQUEST.value());
				rs.setMessage("You are outside the service area");
				rs.setData(null);
				return rs;
			}

			// 1. live location: saved on every poll, so it is shared until the rider is
			// inactive
			Map<String, String> live = new HashMap<>();
			live.put("latitude", String.valueOf(coordinate.getLatitude()));
			live.put("longitude", String.valueOf(coordinate.getLongitue()));
			live.put("vehicleType", vechile_type);
			live.put("status", onRide ? "ON_RIDE" : "AVAILABLE");
			live.put("bookingId", onRide ? rideBookingId : "");
			live.put("updatedAt", String.valueOf(System.currentTimeMillis()));
			stringRedisTemplate.opsForHash().putAll(LIVE_LOCATION_KEY + member, live);
			stringRedisTemplate.expire(LIVE_LOCATION_KEY + member, ALIVE_TTL);

			// 2. matching pool: only riders who can take a new ride
			if (!onRide) {
				stringRedisTemplate.opsForGeo().add(ACTIVE_RIDERS_KEY + vechile_type,
						new Point(coordinate.getLongitue(), coordinate.getLatitude()), member);
				stringRedisTemplate.opsForValue().set(RIDER_ALIVE_KEY + member, vechile_type, ALIVE_TTL);
			}

			rs.setStatuscode(HttpStatus.ACCEPTED.value());
			if (onRide) {
				rs.setMessage("Location updated. You are on a ride, so only your live location is shared");
				rs.setData("Rider " + id + " is on a ride (booking " + rideBookingId + ")");
			} else {
				rs.setMessage("The rider location has been shared with redis");
				rs.setData("Rider " + id + " is active as " + vechile_type);
			}
			return rs;

		} catch (DataAccessException e) {
			e.printStackTrace();
			rs.setStatuscode(HttpStatus.SERVICE_UNAVAILABLE.value());
			rs.setMessage("Unable to share location right now. Please try again");
			rs.setData(null);
			return rs;
		}
	}
//==============Checking the Hyderabad service area==================================================================================

	private boolean isInServiceArea(double lat, double lon) {
		boolean inside = false;
		int n = HYDERABAD_SERVICE_AREA.length;
		for (int i = 0, j = n - 1; i < n; j = i++) {
			double latI = HYDERABAD_SERVICE_AREA[i][0], lonI = HYDERABAD_SERVICE_AREA[i][1];
			double latJ = HYDERABAD_SERVICE_AREA[j][0], lonJ = HYDERABAD_SERVICE_AREA[j][1];
			boolean crosses = (latI > lat) != (latJ > lat) && lon < (lonJ - lonI) * (lat - latI) / (latJ - latI) + lonI;
			if (crosses) {
				inside = !inside;
			}
		}
		return inside;
	}

//============================================================================================================

	// Get All the Assigned rides to the respective rider using their ID

	private static final String ASSIGNED_RIDES_KEY = "assignedrides:";

	public ResponseStructure<List<Map<String, String>>> getassignedrides(Long riderId) {
		ResponseStructure<List<Map<String, String>>> rs = new ResponseStructure<>();
		List<Map<String, String>> rides = new ArrayList<>();

		ScanOptions options = ScanOptions.scanOptions().match(ASSIGNED_RIDES_KEY + riderId + ":*").count(100).build();
		try (Cursor<String> cursor = stringRedisTemplate.scan(options)) {
			while (cursor.hasNext()) {
				String key = cursor.next();
				Map<Object, Object> hash = stringRedisTemplate.opsForHash().entries(key);
				Long ttl = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
				if (hash.isEmpty() || (ttl != null && ttl == -2)) {
					continue;
				}
				Map<String, String> ride = new HashMap<>();
				for (Map.Entry<Object, Object> e : hash.entrySet()) {
					ride.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
				}
				ride.put("expiresInSeconds", String.valueOf(ttl));
				rides.add(ride);

			}

		} catch (DataAccessException e) {
			e.printStackTrace();
			rs.setStatuscode(HttpStatus.SERVICE_UNAVAILABLE.value());
			rs.setMessage("Unable to read assigned rides right now. Please try again");
			rs.setData(null);
			return rs;
		}
		rs.setStatuscode(HttpStatus.OK.value());
		rs.setMessage(rides.isEmpty() ? "No assigned rides for rider " + riderId
				: rides.size() + " assigned ride(s) found for rider " + riderId);
		rs.setData(rides);
		return rs;
	}

//	===========================================================================================================

	@Value("${customer.service.url}")
	private String customerServiceUrl;

	private final RestTemplate restTemplate = new RestTemplate();

	private static final String BOOKING_ACCEPTED_KEY = "booking:accepted:";
	private static final String RIDER_BUSY_KEY = "rider:busy:";

	public ResponseStructure<String> acceptride(Long riderId, Long bookingId) {
		ResponseStructure<String> rs = new ResponseStructure<>();

		Rider rider = riderRepositary.findById(riderId).orElse(null);
		if (rider == null) {
			rs.setStatuscode(HttpStatus.NOT_FOUND.value());
			rs.setMessage("Rider not found with id " + riderId);
			rs.setData(null);
			return rs;
		}

		if ("BUSY".equalsIgnoreCase(rider.getStatus())) {
			rs.setStatuscode(HttpStatus.CONFLICT.value());
			rs.setMessage("You are already on a ride");
			rs.setData(null);
			return rs;
		}

		if ("BLOCKED".equalsIgnoreCase(rider.getStatus())) {
			rs.setStatuscode(HttpStatus.FORBIDDEN.value());
			rs.setMessage(
					"Your account is blocked because your wallet is below " + (int) WALLET_MIN + ". Please recharge");
			rs.setData(null);
			return rs;
		}

		String offerKey = ASSIGNED_RIDES_KEY + riderId + ":" + bookingId;
		if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(offerKey))) {
			rs.setStatuscode(HttpStatus.NOT_FOUND.value());
			rs.setMessage("This ride is no longer available. It may have expired or been taken by another rider");
			rs.setData(null);
			return rs;
		}

		String claimKey = BOOKING_ACCEPTED_KEY + bookingId;
		Boolean claimed = stringRedisTemplate.opsForValue().setIfAbsent(claimKey, String.valueOf(riderId),
				Duration.ofMinutes(10));
		if (!Boolean.TRUE.equals(claimed)) {
			rs.setStatuscode(HttpStatus.CONFLICT.value());
			rs.setMessage("This ride has already been accepted by another rider");
			rs.setData(null);
			return rs;
		}

		try {
			ResponseEntity<ResponseStructure<String>> reply = restTemplate.exchange(
					customerServiceUrl + "/customer/booking/assignrider/" + bookingId + "/" + riderId, HttpMethod.PUT,
					null, new ParameterizedTypeReference<ResponseStructure<String>>() {
					});
			ResponseStructure<String> body = reply.getBody();

			if (body == null || body.getStatuscode() != 200) {
				stringRedisTemplate.delete(claimKey);
				rs.setStatuscode(HttpStatus.CONFLICT.value());
				rs.setMessage(body == null ? "No reply from customer service" : body.getMessage());
				rs.setData(null);
				return rs;
			}
		} catch (RestClientException e) {
			stringRedisTemplate.delete(claimKey);
			e.printStackTrace();
			rs.setStatuscode(HttpStatus.SERVICE_UNAVAILABLE.value());
			rs.setMessage("Unable to reach customer service. Please try again");
			rs.setData(null);
			return rs;
		}

		rider.setStatus("BUSY");
		riderRepositary.save(rider);

		try {
			String member = String.valueOf(riderId);
			for (String t : new String[] { "BIKE", "AUTO", "CAR" }) {
				stringRedisTemplate.opsForGeo().remove(ACTIVE_RIDERS_KEY + t, member);
			}
			stringRedisTemplate.delete(RIDER_ALIVE_KEY + riderId);
			stringRedisTemplate.opsForValue().set(RIDER_BUSY_KEY + riderId, String.valueOf(bookingId));
			deleteKeys(ASSIGNED_RIDES_KEY + riderId + ":*");
			deleteKeys(ASSIGNED_RIDES_KEY + "*:" + bookingId);
		} catch (DataAccessException e) {
			System.err.println("Redis cleanup after accept failed: " + e.getMessage());
		}
		rs.setStatuscode(HttpStatus.OK.value());
		rs.setMessage("Ride accepted");
		rs.setData("Booking " + bookingId + " is assigned to rider " + riderId);
		return rs;
	}

	// =====helper method==============
	private void deleteKeys(String pattern) {
		List<String> keys = new ArrayList<>();
		try (Cursor<String> cursor = stringRedisTemplate
				.scan(ScanOptions.scanOptions().match(pattern).count(100).build())) {
			while (cursor.hasNext()) {
				keys.add(cursor.next());
			}
		}
		if (!keys.isEmpty()) {
			stringRedisTemplate.delete(keys);
		}
	}

//============Navigation: pickup, drop and arrival=========================================================

	// Google Maps URL (no API key needed). The rider's phone opens it and Google
	// Maps
	// starts turn-by-turn navigation from the phone's current location.
	private static final String GOOGLE_MAPS_DIR_URL = "https://www.google.com/maps/dir/";

	private String googleMapsLink(double lat, double lon) {
		return GOOGLE_MAPS_DIR_URL + "?api=1&destination=" + lat + "%2C" + lon + "&dir_action=navigate";
	}

	// asks the Customer service for the booking
	private ResponseStructure<BookingInfoDTO> fetchBooking(Long bookingId) {
		ResponseEntity<ResponseStructure<BookingInfoDTO>> reply = restTemplate.exchange(
				customerServiceUrl + "/customer/booking/" + bookingId, HttpMethod.GET, null,
				new ParameterizedTypeReference<ResponseStructure<BookingInfoDTO>>() {
				});
		return reply.getBody();
	}

	public ResponseStructure<Map<String, String>> movetowardspickup(Long riderId, Long bookingId) {
		return navigateTo(riderId, bookingId, true);
	}

	public ResponseStructure<Map<String, String>> movetowardsdrop(Long riderId, Long bookingId) {
		return navigateTo(riderId, bookingId, false);
	}

	// one method for both directions: toPickup = true -> pickup, false -> drop
	private ResponseStructure<Map<String, String>> navigateTo(Long riderId, Long bookingId, boolean toPickup) {
		ResponseStructure<Map<String, String>> rs = new ResponseStructure<>();

		// 1. get the booking from the Customer service
		BookingInfoDTO booking;
		try {
			ResponseStructure<BookingInfoDTO> reply = fetchBooking(bookingId);
			if (reply == null || reply.getStatuscode() != 200 || reply.getData() == null) {
				rs.setStatuscode(reply == null ? HttpStatus.BAD_GATEWAY.value() : reply.getStatuscode());
				rs.setMessage(reply == null ? "No reply from customer service" : reply.getMessage());
				rs.setData(null);
				return rs;
			}
			booking = reply.getData();
		} catch (RestClientException e) {
			e.printStackTrace();
			rs.setStatuscode(HttpStatus.SERVICE_UNAVAILABLE.value());
			rs.setMessage("Unable to reach customer service. Please try again");
			rs.setData(null);
			return rs;
		}

		// 2. the booking must belong to this rider
		if (booking.getRiderId() == null || !booking.getRiderId().equals(riderId)) {
			rs.setStatuscode(HttpStatus.FORBIDDEN.value());
			rs.setMessage("This booking is not assigned to rider " + riderId);
			rs.setData(null);
			return rs;
		}

		// 3. the ride must be in the right stage
		String status = booking.getStatus();
		boolean allowed = toPickup ? "RIDER_ASSIGNED".equals(status) : "RIDE_STARTED".equals(status);
		if (!allowed) {
			rs.setStatuscode(HttpStatus.CONFLICT.value());
			rs.setMessage(toPickup
					? "You can navigate to the pickup only right after accepting the ride. Current status: " + status
					: "You can navigate to the drop only after the OTP is verified and the ride has started. Current status: "
							+ status);
			rs.setData(null);
			return rs;
		}

		// 4. pick the destination and build the Google Maps link
		double lat = toPickup ? booking.getPickupLat() : booking.getDropLat();
		double lon = toPickup ? booking.getPickupLon() : booking.getDropLon();
		String name = toPickup ? booking.getPickupLocation() : booking.getDropLocation();

		Map<String, String> nav = new LinkedHashMap<>();
		nav.put("bookingId", String.valueOf(bookingId));
		nav.put("destinationType", toPickup ? "PICKUP" : "DROP");
		nav.put("destinationName", name == null ? "" : name);
		nav.put("latitude", String.valueOf(lat));
		nav.put("longitude", String.valueOf(lon));
		nav.put("rideStatus", status);
		nav.put("googleMapsUrl", googleMapsLink(lat, lon));

		rs.setStatuscode(HttpStatus.OK.value());
		rs.setMessage(toPickup ? "Navigate to the pickup location" : "Navigate to the drop location");
		rs.setData(nav);
		return rs;
	}

	// Rider reached the pickup: the Customer service changes the booking status
	public ResponseStructure<String> updatearrival(Long riderId, Long bookingId) {
		ResponseStructure<String> rs = new ResponseStructure<>();

		try {
			ResponseEntity<ResponseStructure<String>> reply = restTemplate.exchange(
					customerServiceUrl + "/customer/booking/arrived/" + bookingId + "/" + riderId, HttpMethod.PUT, null,
					new ParameterizedTypeReference<ResponseStructure<String>>() {
					});
			ResponseStructure<String> body = reply.getBody();

			if (body == null) {
				rs.setStatuscode(HttpStatus.BAD_GATEWAY.value());
				rs.setMessage("No reply from customer service");
				rs.setData(null);
				return rs;
			}

			if (body.getStatuscode() != 200) {
				rs.setStatuscode(body.getStatuscode());
				rs.setMessage(body.getMessage());
				rs.setData(null);
				return rs;
			}
		} catch (RestClientException e) {
			e.printStackTrace();
			rs.setStatuscode(HttpStatus.SERVICE_UNAVAILABLE.value());
			rs.setMessage("Unable to reach customer service. Please try again");
			rs.setData(null);
			return rs;
		}

		rs.setStatuscode(HttpStatus.OK.value());
		rs.setMessage("Arrival updated. You have reached the pickup location");
		rs.setData("Booking " + bookingId + " status is now RIDER_ARRIVED");
		return rs;
	}

	public ResponseStructure<String> verifyotp(String otp, Long riderId, Long bookingId) {
		ResponseStructure<String> rs = new ResponseStructure<>();

		try {
			ResponseEntity<ResponseStructure<String>> reply = restTemplate.exchange(
					customerServiceUrl + "/customer/booking/verifyotp/" + bookingId + "/" + riderId + "?otp={otp}",
					HttpMethod.PUT, null, new ParameterizedTypeReference<ResponseStructure<String>>() {
					}, otp);
			ResponseStructure<String> body = reply.getBody();

			if (body == null) {
				rs.setStatuscode(HttpStatus.BAD_GATEWAY.value());
				rs.setMessage("No reply from customer service");
				rs.setData(null);
				return rs;
			}

			if (body.getStatuscode() != 200) {
				rs.setStatuscode(body.getStatuscode());
				rs.setMessage(body.getMessage());
				rs.setData(null);
				return rs;
			}
		} catch (RestClientException e) {
			e.printStackTrace();
			rs.setStatuscode(HttpStatus.SERVICE_UNAVAILABLE.value());
			rs.setMessage("Unable to reach customer service. Please try again");
			rs.setData(null);
			return rs;
		}

		rs.setStatuscode(HttpStatus.OK.value());
		rs.setMessage("OTP verified. Ride started");
		rs.setData("Booking " + bookingId + " status is now RIDE_STARTED");
		return rs;
	}

	public ResponseStructure<Map<String, Object>> completeride(Long riderId, Long bookingId) {
		ResponseStructure<Map<String, Object>> rs = new ResponseStructure<>();

		// 1. the rider must exist (checked first, so nothing changes if he doesn't)
		Rider rider = riderRepositary.findById(riderId).orElse(null);
		if (rider == null) {
			rs.setStatuscode(HttpStatus.NOT_FOUND.value());
			rs.setMessage("Rider not found with id " + riderId);
			rs.setData(null);
			return rs;
		}

		// 2. the Customer service completes the booking and returns its information
		BookingInfoDTO booking;
		try {
			ResponseEntity<ResponseStructure<BookingInfoDTO>> reply = restTemplate.exchange(
					customerServiceUrl + "/customer/booking/complete/" + bookingId + "/" + riderId, HttpMethod.PUT,
					null, new ParameterizedTypeReference<ResponseStructure<BookingInfoDTO>>() {
					});
			ResponseStructure<BookingInfoDTO> body = reply.getBody();

			if (body == null) {
				rs.setStatuscode(HttpStatus.BAD_GATEWAY.value());
				rs.setMessage("No reply from customer service");
				rs.setData(null);
				return rs;
			}
			if (body.getStatuscode() != 200 || body.getData() == null) {
				rs.setStatuscode(body.getStatuscode() != 200 ? body.getStatuscode() : HttpStatus.BAD_GATEWAY.value());
				rs.setMessage(body.getMessage());
				rs.setData(null);
				return rs;
			}
			booking = body.getData();
		} catch (RestClientException e) {
			e.printStackTrace();
			rs.setStatuscode(HttpStatus.SERVICE_UNAVAILABLE.value());
			rs.setMessage("Unable to reach customer service. Please try again");
			rs.setData(null);
			return rs;
		}

		// 3. 10% of the fare is deducted from the wallet
		double commission = Math.round(booking.getFare() * COMMISSION_RATE * 100.0) / 100.0;
		double wallet = Math.round((rider.getWallet() - commission) * 100.0) / 100.0;
		rider.setWallet(wallet);

		// 4. wallet below 1000 -> BLOCKED, otherwise AVAILABLE
		boolean blocked = wallet < WALLET_MIN;
		rider.setStatus(blocked ? "BLOCKED" : "AVAILABLE");
		riderRepositary.save(rider);

		// 5. Redis: the rider is no longer on a ride
		try {
			stringRedisTemplate.delete(RIDER_BUSY_KEY + riderId);
			stringRedisTemplate.delete(BOOKING_ACCEPTED_KEY + bookingId);
			if (blocked) {
				stringRedisTemplate.opsForValue().set(RIDER_BLOCKED_KEY + riderId, "1");
			} else {
				stringRedisTemplate.delete(RIDER_BLOCKED_KEY + riderId);
			}
		} catch (DataAccessException e) {
			System.err.println("Redis cleanup after complete failed: " + e.getMessage());
		}

		// 6. complete booking information + wallet details go back to the rider
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("booking", booking);
		data.put("commissionDeducted", commission);
		data.put("walletBalance", wallet);
		data.put("riderStatus", rider.getStatus());

		rs.setStatuscode(HttpStatus.OK.value());
		rs.setMessage(blocked ? "Ride completed. Your wallet is below " + (int) WALLET_MIN
				+ ", so your account is blocked. Please recharge" : "Ride completed");
		rs.setData(data);
		return rs;
	}

	public ResponseStructure<String> goinactive(Long riderId) {
		ResponseStructure<String> rs = new ResponseStructure<>();

		try {
			// a rider on a ride must finish it first
			if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(RIDER_BUSY_KEY + riderId))) {
				rs.setStatuscode(HttpStatus.CONFLICT.value());
				rs.setMessage("You are on a ride. Complete the ride before going inactive");
				rs.setData(null);
				return rs;
			}

			String member = String.valueOf(riderId);
			for (String t : new String[] { "BIKE", "AUTO", "CAR" }) {
				stringRedisTemplate.opsForGeo().remove(ACTIVE_RIDERS_KEY + t, member);
			}
			stringRedisTemplate.delete(RIDER_ALIVE_KEY + riderId);
			stringRedisTemplate.delete(LIVE_LOCATION_KEY + riderId);
			deleteKeys(ASSIGNED_RIDES_KEY + riderId + ":*"); // pending offers are void now
		} catch (DataAccessException e) {
			e.printStackTrace();
			rs.setStatuscode(HttpStatus.SERVICE_UNAVAILABLE.value());
			rs.setMessage("Unable to go inactive right now. Please try again");
			rs.setData(null);
			return rs;
		}

		rs.setStatuscode(HttpStatus.OK.value());
		rs.setMessage("You are inactive. Stop sending your location");
		rs.setData("Rider " + riderId + " removed from active riders");
		return rs;
	}

	private double round2(double value) {
		return Math.round(value * 100.0) / 100.0;
	}

	// rider ride history: the bookings live in the Customer service
	public ResponseStructure<List<BookingInfoDTO>> ridehistory(Long riderId, String status) {
		ResponseStructure<List<BookingInfoDTO>> rs = new ResponseStructure<>();

		if (!riderRepositary.existsById(riderId)) {
			rs.setStatuscode(HttpStatus.NOT_FOUND.value());
			rs.setMessage("Rider not found with id " + riderId);
			rs.setData(null);
			return rs;
		}

		try {
			ResponseEntity<ResponseStructure<List<BookingInfoDTO>>> reply = restTemplate.exchange(
					customerServiceUrl + "/customer/booking/history/rider?riderId={riderId}&status={status}",
					HttpMethod.GET, null, new ParameterizedTypeReference<ResponseStructure<List<BookingInfoDTO>>>() {
					}, riderId, status);
			ResponseStructure<List<BookingInfoDTO>> body = reply.getBody();

			if (body == null) {
				rs.setStatuscode(HttpStatus.BAD_GATEWAY.value());
				rs.setMessage("No reply from customer service");
				rs.setData(null);
				return rs;
			}

			// the Customer service already checked the status and built the list, so pass
			// it on
			rs.setStatuscode(body.getStatuscode());
			rs.setMessage(body.getMessage());
			rs.setData(body.getData());
			return rs;

		} catch (RestClientException e) {
			e.printStackTrace();
			rs.setStatuscode(HttpStatus.SERVICE_UNAVAILABLE.value());
			rs.setMessage("Unable to reach customer service. Please try again");
			rs.setData(null);
			return rs;
		}
	}

	// earnings of a rider between two dates (same date = that single day)
	public ResponseStructure<Map<String, Object>> earningsummary(Long riderId, String from, String to) {
		ResponseStructure<Map<String, Object>> rs = new ResponseStructure<>();

		// 1. both dates are required
		if (from == null || from.isBlank() || to == null || to.isBlank()) {
			rs.setStatuscode(HttpStatus.BAD_REQUEST.value());
			rs.setMessage("Both from and to dates are required, in yyyy-MM-dd format");
			rs.setData(null);
			return rs;
		}

		// 2. real dates in yyyy-MM-dd (2026-02-30 and 09-10-2026 are rejected)
		LocalDate fromDate;
		LocalDate toDate;
		try {
			fromDate = LocalDate.parse(from.trim());
			toDate = LocalDate.parse(to.trim());
		} catch (DateTimeParseException e) {
			rs.setStatuscode(HttpStatus.BAD_REQUEST.value());
			rs.setMessage("Dates must be valid and in yyyy-MM-dd format, for example 2026-10-09");
			rs.setData(null);
			return rs;
		}

		// 3. logical checks
		LocalDate today = LocalDate.now();
		if (fromDate.isAfter(toDate)) {
			rs.setStatuscode(HttpStatus.BAD_REQUEST.value());
			rs.setMessage("The from date cannot be after the to date");
			rs.setData(null);
			return rs;
		}
		if (toDate.isAfter(today)) {
			rs.setStatuscode(HttpStatus.BAD_REQUEST.value());
			rs.setMessage("Dates cannot be in the future");
			rs.setData(null);
			return rs;
		}
		if (ChronoUnit.DAYS.between(fromDate, toDate) > 365) {
			rs.setStatuscode(HttpStatus.BAD_REQUEST.value());
			rs.setMessage("The date range cannot be more than 366 days");
			rs.setData(null);
			return rs;
		}

		// 4. the rider must exist
		if (!riderRepositary.existsById(riderId)) {
			rs.setStatuscode(HttpStatus.NOT_FOUND.value());
			rs.setMessage("Rider not found with id " + riderId);
			rs.setData(null);
			return rs;
		}

		// 5. the Customer service counts the rides and adds up fare and distance in one
		// query
		EarningSummaryDTO summary;
		try {
			ResponseEntity<ResponseStructure<EarningSummaryDTO>> reply = restTemplate.exchange(
					customerServiceUrl + "/customer/booking/summary/rider?riderId={riderId}&from={from}&to={to}",
					HttpMethod.GET, null, new ParameterizedTypeReference<ResponseStructure<EarningSummaryDTO>>() {
					}, riderId, fromDate.toString(), toDate.toString());
			ResponseStructure<EarningSummaryDTO> body = reply.getBody();

			if (body == null || body.getStatuscode() != 200 || body.getData() == null) {
				rs.setStatuscode(body == null ? HttpStatus.BAD_GATEWAY.value() : body.getStatuscode());
				rs.setMessage(body == null ? "No reply from customer service" : body.getMessage());
				rs.setData(null);
				return rs;
			}
			summary = body.getData();
		} catch (RestClientException e) {
			e.printStackTrace();
			rs.setStatuscode(HttpStatus.SERVICE_UNAVAILABLE.value());
			rs.setMessage("Unable to reach customer service. Please try again");
			rs.setData(null);
			return rs;
		}

		// 6. earnings: the 10% commission is the same one deducted from the wallet
		// after each ride
		double totalFare = round2(summary.getTotalFare());
		double commission = round2(totalFare * COMMISSION_RATE);
		double totalEarning = round2(totalFare - commission);

		boolean singleDay = fromDate.equals(toDate);
		String period = !singleDay ? "RANGE" : (fromDate.equals(today) ? "TODAY" : "SINGLE_DAY");

		Map<String, Object> data = new LinkedHashMap<>();
		data.put("riderId", riderId);
		data.put("period", period);
		data.put("from", fromDate.toString());
		data.put("to", toDate.toString());
		data.put("rides", summary.getRides());
		data.put("totalFare", totalFare);
		data.put("commissionDeducted", commission);
		data.put("totalEarning", totalEarning);
		data.put("totalDistanceKm", round2(summary.getTotalDistanceKm()));

		rs.setStatuscode(HttpStatus.OK.value());
		rs.setMessage(summary.getRides() == 0 ? "No completed rides in this period"
				: period.equals("TODAY") ? "Today's earnings" : "Earnings summary");
		rs.setData(data);
		return rs;
	}

}