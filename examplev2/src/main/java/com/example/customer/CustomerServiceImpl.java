package com.example.customer;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Simple implementation of a customer service, uses an in-memory map for the sake of simplicity. The two "read" methods
 * simulate slow queries by introducing a random delay.
 * 
 * @author tyler.vangorder
 *
 */
@Service
public class CustomerServiceImpl implements CustomerService {

	@Autowired
	CustomerDao customerDao;
	
	AtomicLong nextId = new AtomicLong(0);
	
	@Override
	public Customer saveCustomer(Customer customer) {
		return customerDao.saveCustomer(customer);
	}

	@Override
	public Customer getCustomerById(Long customerId) {
		return customerDao.getCustomerById(customerId);
	}

	@Override
	public List<Customer> findCustomers(CustomerCriteria criteria) {
		return customerDao.findCustomers(criteria);
	}

}
