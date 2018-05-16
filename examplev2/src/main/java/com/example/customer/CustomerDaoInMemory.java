package com.example.customer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Component
public class CustomerDaoInMemory implements CustomerDao {

	List<Customer> customerList = new ArrayList<>();

	AtomicLong nextId = new AtomicLong(0);
	
	@Override
	public Customer saveCustomer(Customer customer) {
		Assert.notNull(customer, "A customer must be provided");
		Assert.notNull(customer.getEmail(), "A customer cannot be added without an email address.");
		Assert.notNull(customer.getFirstName(), "A customer cannot be added without a first name.");
		Assert.notNull(customer.getLastName(), "A customer cannot be added without a last name.");

		if (customer.getCustomerId() != null) {
			CustomerCriteria criteria = new CustomerCriteria();
			criteria.setCustomerId(customer.getCustomerId());
			Customer existingCustomer = findCustomers(criteria).stream()
					.findFirst()
					.orElseThrow(() -> new CustomerNotFoundException(customer.getCustomerId()));
			existingCustomer.setEmail(customer.getEmail());
			existingCustomer.setFirstName(customer.getFirstName());
			existingCustomer.setLastName(customer.getLastName());
			existingCustomer.setAddress(customer.getAddress());
			
			return existingCustomer;
		} else {
			customer.setCustomerId(nextId.getAndIncrement());
			customerList.add(customer);
			return customer;
		}
	}

	@Override
	public Customer getCustomerById(Long customerId) {
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return customerList.stream()
				.filter(c -> nullSafeEquals(customerId, c.getCustomerId()))
				.findFirst().orElseThrow(() -> new CustomerNotFoundException(customerId));
	}

	@Override
	public List<Customer> findCustomers(final CustomerCriteria criteria) {
		if (criteria == null || (criteria.getCustomerId() == null && !StringUtils.hasText(criteria.getLastName()))) {
			return customerList;
		}
				
		return customerList.stream()
			.filter(c -> criteria.getCustomerId() == null || nullSafeEquals(criteria.getCustomerId(), c.getCustomerId()))
			.filter(c -> criteria.getLastName() == null || nullSafeEquals(criteria.getLastName(), c.getLastName()))
			.collect(Collectors.toList());

	}
	
	private static boolean nullSafeEquals(Object o1, Object o2) {
		if (o1 == null) {
			return o2 == null;
		}
		return o2 != null && o1.equals(o2);
	}

	@PostConstruct
	public void initCustomerMap() {
		//Add some seed data to work with.
		customerList = new ArrayList<>();
		nextId.set(0);
		Customer customer = new Customer(nextId.getAndIncrement(), "mchammer@tolegittoquit.com", "Burrell", "Stanley");
		customer.setAddress(new Address("44896 Vista Del Sol", "Fremont", "CA", "94539"));		
		customerList.add(customer);

		customer = new Customer(nextId.getAndIncrement(), "cc@realmusician.com", "Cornell", "Chris");
		customer.setAddress(new Address("7400 Sand Point Way NE", "Seattle", "WA", "98115"));
		customerList.add(customer);

		customer = new Customer(nextId.getAndIncrement(), "vanilla@ice.com", "Van Winkle", "Robert");
		customer.setAddress(new Address("10775 Versailles Blvd", "Wellington", "FL", "33449"));
		customerList.add(customer);
	}
	
}
