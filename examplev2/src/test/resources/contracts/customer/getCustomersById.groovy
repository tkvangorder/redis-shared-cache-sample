org.springframework.cloud.contract.spec.Contract.make {
    description "Should return a valid customer object"
    request {
        method 'GET'
        url '/customers/1'
    }
    response {
        status 200
        body '''
        {
            "customerId": 1,
            "email": "john.smith@gmail.com",
            "lastName": "Smith",
            "firstName": "John"
        }
        '''
    }
}