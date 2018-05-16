package com.example.customer;

import java.io.Serializable;

public class Address implements Serializable {

	private static final long serialVersionUID = 1L;

	String address1;
	String address2;
	String city;
	String state;
	String postalCode;
	
	public Address() {
	}
	
	public Address(String address1, String city, String state, String postalCode) {
		this.address1 = address1;
		this.city = city;
		this.state = state;
		this.postalCode = postalCode;
	}

	public Address(String address1, String address2, String city, String state, String postalCode) {
		this(address1, city, state, postalCode);
		this.address2 = address2;
	}
	
	public String getAddress1() {
		return address1;
	}
	public void setAddress1(String address1) {
		this.address1 = address1;
	}
	public String getAddress2() {
		return address2;
	}
	public void setAddress2(String address2) {
		this.address2 = address2;
	}
	public String getCity() {
		return city;
	}
	public void setCity(String city) {
		this.city = city;
	}
	public String getState() {
		return state;
	}
	public void setState(String state) {
		this.state = state;
	}
	public String getPostalCode() {
		return postalCode;
	}
	public void setPostalCode(String postalCode) {
		this.postalCode = postalCode;
	}

}
