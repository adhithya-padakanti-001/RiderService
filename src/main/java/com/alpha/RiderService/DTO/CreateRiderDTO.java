package com.alpha.RiderService.DTO;

import com.alpha.RiderService.Entity.Vechile;

public class CreateRiderDTO {
	String name;
	long phone;
	String email;
	String gender;
	String status;
	String drivinglicence;
	private Vechile vechile;

	public CreateRiderDTO() {
		super();
		// TODO Auto-generated constructor stub
	}

	public CreateRiderDTO(String name, long phone, String email, String gender, String status, String drivinglicence,
			Vechile vechile) {
		super();
		this.name = name;
		this.phone = phone;
		this.email = email;
		this.gender = gender;
		this.status = status;
		this.drivinglicence = drivinglicence;
		this.vechile = vechile;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public long getPhone() {
		return phone;
	}

	public void setPhone(long phone) {
		this.phone = phone;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getGender() {
		return gender;
	}

	public void setGender(String gender) {
		this.gender = gender;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getDrivinglicence() {
		return drivinglicence;
	}

	public void setDrivinglicence(String drivinglicence) {
		this.drivinglicence = drivinglicence;
	}

	public Vechile getVechile() {
		return vechile;
	}

	public void setVechile(Vechile vechile) {
		this.vechile = vechile;
	}

}
