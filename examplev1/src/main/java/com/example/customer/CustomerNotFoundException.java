package com.example.customer;

public class CustomerNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public CustomerNotFoundException() {
		super("The Customer was not found.");
	}

	public CustomerNotFoundException(Long customerId) {
		super(String.format("The Customer [%s] was not found.", customerId));
	}
}
