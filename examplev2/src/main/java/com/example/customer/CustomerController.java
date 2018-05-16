package com.example.customer;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value="/customers", produces= {MediaType.APPLICATION_JSON_VALUE})
public class CustomerController {
	
	private static final Logger logger = LoggerFactory.getLogger(CustomerController.class);
	 
	@Autowired
	CustomerService customerService;
	
	@PostMapping("")
	public Customer saveCustomer(@RequestBody Customer customer) {
		return customerService.saveCustomer(customer);
	}
	
	@GetMapping(value="/{customerId}")
	public Customer getCustomerById(@PathVariable("customerId") Long customerId) {
		Customer customer = customerService.getCustomerById(customerId);
		if (customer != null && logger.isInfoEnabled()) {
			logger.info("The customer [{}] lives in the [{}].", customerId, customer.getAddress().getCity());
		}
		return customer;
		
	}

	@GetMapping(value="")
	public List<Customer> findCustomersByLastNmae(@RequestParam(value="lastName", required=false) String lastName) {
		CustomerCriteria criteria = new CustomerCriteria();
		criteria.setLastName(lastName);
		return customerService.findCustomers(criteria);
	}
}
