package com.example.customer;

public class Customer {

	Long customerId;
	String email;
	String lastName;
	String firstName;

	public Customer() {
	}
	
	public Customer(Long customerId, String email, String lastName, String firstName) {
		this.customerId = customerId;
		this.email = email;		
		this.lastName = lastName;
		this.firstName = firstName;
	}
	
	public Long getCustomerId() {
		return customerId;
	}
	public void setCustomerId(Long customerId) {
		this.customerId = customerId;
	}
	public String getEmail() {
		return email;
	}
	public void setEmail(String email) {
		this.email = email;
	}
	public String getLastName() {
		return lastName;
	}
	public void setLastName(String lastName) {
		this.lastName = lastName;
	}
	public String getFirstName() {
		return firstName;
	}
	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}
	
}
