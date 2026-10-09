package com.alpha.RiderService.Entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;

@Entity
public class Rider {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;
	private String name;
	private long phone;
	private String email;
	private String gender;
	private double wallet;
	private int numberOfrides;
	private String status;
	private String drivinglicence;;
	@OneToOne(cascade = CascadeType.ALL)
	private Vechile vechile;

	public Rider() {
		super();
		// TODO Auto-generated constructor stub
	}

	public Rider(String name, long phone, String email, String gender, double wallet, int numberOfrides, String status,
			String drivinglicence, Vechile vechile) {
		super();
		this.name = name;
		this.phone = phone;
		this.email = email;
		this.gender = gender;
		this.wallet = wallet;
		this.numberOfrides = numberOfrides;
		this.status = status;
		this.drivinglicence = drivinglicence;
		this.vechile = vechile;
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
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

	public double getWallet() {
		return wallet;
	}

	public void setWallet(double wallet) {
		this.wallet = wallet;
	}

	public int getNumberOfrides() {
		return numberOfrides;
	}

	public void setNumberOfrides(int numberOfrides) {
		this.numberOfrides = numberOfrides;
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
