package com.alpha.RiderService.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Vechile {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private int id;
	private String type;
	@Column(unique = true )
	private String vechilenumber;
	private String vechileName;
	private String modelName;
	private String status;

	public Vechile() {
		super();
		// TODO Auto-generated constructor stub
	}

	public Vechile(String type, String vechilenumber, String vechileName, String modelName, String status) {
		super();
		this.type = type;
		this.vechilenumber = vechilenumber;
		this.vechileName = vechileName;
		this.modelName = modelName;
		this.status = status;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getVechilenumber() {
		return vechilenumber;
	}

	public void setVechilenumber(String vechilenumber) {
		this.vechilenumber = vechilenumber;
	}

	public String getVechileName() {
		return vechileName;
	}

	public void setVechileName(String vechileName) {
		this.vechileName = vechileName;
	}

	public String getModelName() {
		return modelName;
	}

	public void setModelName(String modelName) {
		this.modelName = modelName;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

}
