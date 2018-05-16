package com.example.customer;

import java.util.List;

public interface CustomerDao {

	public Customer saveCustomer(Customer customer);
	public Customer getCustomerById(Long customerId);
	
	public List<Customer> findCustomers(CustomerCriteria criteria);
	
}
