package com.example.customer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

//Note that the @EnableCache is also gone, the unified-cache library will auto-configure this!
@SpringBootApplication
public class CustomerApplication {

	public static void main(String[] args) {
		SpringApplication.run(CustomerApplication.class, args);
	}

	//Redis template is now gone, because the unified-cache library will handle this for us.
}
