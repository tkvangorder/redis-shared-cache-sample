# Evolving Your Distrubuted Cache In a Continuous Delivery World

This project contains a demonstration of how the Spring caching abstraction can be customized to allow multiple versions of an application to "share" the same distributed Redis cache even when the structure of the values has changed between those versions of the software. This project is structured in such a way that it will walk through the various problems you will encounter when sharing a distributed cache.

There are three projects in this library: 

- examplev1 This is a simple Spring boot application that provides rest endpoints to save/get customers.
- examplev2 This is a duplicate of the first application, except that the Customer Model has been evolved to include a nested Address model.
- unified-cache This is library that can be added as a dependency to any project and it will enable redis caching and the unified caching model via Spring Boot auto-configuration.

## NOTES:

- The example rest application uses an in-memory approach to data access and has an artificial 5 second delay when retrieving a customer by their ID. This method is also the one in which caching is applied.
- The version that is currently on master is the "Final"/working version that demonstrates how the unified cache works, see "Version 4 and 4a below".

## Setup
- There is a docker-compose.yml file in this project that can be used to start Redis in a docker container. The example assumes docker is running on your local machine.
- I recommend using a tool like redis desktop manager to connect to your redis instance to examine how the keys and objects are being stored.
- Postman is another tool that makes it convenient when calling the various rest endpoints in the examples. 
- The file "Caching Samples.postman_collection.json" can be imported into postman to aid in the walk-through.
- This project uses maven and can be built via "mvn clean package"

## Version 1 - Running the application without any caching in place.

## Version 2 - Running the application with Spring's default caching.

## Version 3 - Running the application with serialization verification enabled.

## Version 4 - Running the application with the unified caching model.

## Version 4a - Demonstration of how promotion works.

