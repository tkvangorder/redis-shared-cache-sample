# Evolving Your Distributed Cache In a Continuous Delivery World

This project contains a demonstration of how the Spring caching abstraction can be customized to allow multiple versions of an application to "share" the same distributed Redis cache even when the structure of the values has changed between those versions of the software. This project is structured in such a way that it will walk through the various problems you will encounter when sharing a distributed cache.

The code in this project was developed to support a talk that I give on the same subject and the slide deck can be found here: https://docs.google.com/presentation/d/1LZCR1o16xfoJRz0At_hNOA4ioHgrx_NUvpQQj0N2uNU/edit?usp=sharing

## What do we mean by "continuous delivery"?

A continuous delivery model is one in which, through an automated pipeline, changes made by the developers are built, unit and integration tests are run, the application is rolled out through lower environments. The roll out to production is also automated and may be triggered by a manual step or through a continous deployment tool.

In a high volume system it is very common to have a cluster of nodes that have been scaled out horizontally to handle the traffic. This conceptually looks like the same application/service that has been cloned N number of times to perform the same function. The strategies that are used to upgrade a production system typically require two different versions of the application to be running concurrently to insure 24/7 availability.

For the sake of this exmaple we will assume a strategy that is using rolling deployments, there are other strategies that leverage cloud resources that result in a similar problem; there are two versions of the software running concurrently for a period of time and care must be taken to insure breaking changes are not introduced. 

## What constitutes a breaking change?

Any time you change the structure of your domain model, there is a potential for a breaking change. 

- Renaming or deleting an attribute in your model (or column in your database)
- Adding attributes to your model. (Wait, what? How does an additive change impact existing software?)

(There are other types of breaking changes, but the changes to a domain model have more impact with respect to distributed caching.)

## What does a distributed cache have to do with this?

A distributed cache is a server or collection of servers that sits in your infrastructure and provides a mechanism to quickly store and retrieve data (typically by storing the data in-memory). This makes the cache significantly faster than retrieving the value from a database that may need to access that data from disk, use indexes, and possibly join entities (in a relational model). A distributed cache works by using a unique key (derived from the model you are going to cache) and then serializing that data into the distributed cache.

It turns out that model changes can cause some havoc when you have two different versions of an application that are using the same cache.

  - An existing version of the software might cache a value under a key that does NOT include a new attribute, the new version might grab this cached value expecting the new attribute to be populated. This can lead to "bad things" and what is worse, after the item is evicted, the error will magically go away. It's like chasing ghost in the system.
  - What if you build in a solution such that as you deserialize a value from cache that you can verify its structure matches? This works better, as you don't end up getting errors, but can lead to "cache thrashing".
  1. Version A caches value.
  2. Version B gets the value, fails to deserialize, evicts that value from the cache, retrieves its version of the model  and then puts that in the cache.
  3. Version A gets the value, fails to deserialize, evicts that value from the cache, retrieves its version of the model  and then puts that in the cache.
 
## How do you solve this?

One way to solve the problems with caching is to provision a new instance of your distributed cache for each new version of the software. The obvious downside of this approach is that the cache will initially be empty and require a warm-up period.

## A BETTER WAY:

In Redis, it is possible to store a HashSet as a value under a specific key. This means that for a given key, you can store multiple copies of cached data and it is possible to use the application version as the secondary hash key under which to store the serialized data. This means that each version of the application will have its own copy of the cached value. If you combine this with the ability to detect model changes during the deserialization process, you can promote compatible versions of a cached value from one version to the next!

This implementation relies on two customizations: 

1. A custom serializer is used to convert the cached model to JSON. This serializer will encode a model's serialVersionUID into the JSON stream. The deserializer will compare the serialVersionUID stored in the cache with the version in the application. This approach is recursive and any sub-elements are also encoded in the same way. If a version in cache does not match a version in the application, the deserializer will fail. This requires that each cached model implements "Serializable" and that each time the model is altered, a developer must increment the serialVersionUID.

2. The cache implementation has been modified to use a hashset as the value, where the key in the hashset is the application's build version. This relies on the automated deployment process to increment and inject the version into the application. This also means that each version of the application will have its own copy of a cached value. This ends up looking like CACHE_NAME -> UNIQUE KEY -> APPLICATION VERSION -> Cached Value.


### Cache Promotion

If there is a cache "miss" for a specific version of the application, the cache implementation will attempt to find the most recent, previous version's cached value. If the deserialization process succeeds, we know the model stored in cache is compatible with the version expected by the application. This version is promoted and put into the new version's cache and does not require the new version to resolve the value against the underlying data store.

## Project organization

There are three projects in this library: 

- examplev1 This is a simple Spring Boot application that provides REST endpoints to save/get customers.
- examplev2 This is a duplicate of the first application, except that the customer model has been evolved to include a nested address model.
- unified-cache This is library that can be added as a dependency to any project and it will enable redis caching and the unified caching model via Spring Boot auto-configuration.

## NOTES:

- The example rest application uses an in-memory approach to data access and has an artificial 5 second delay when retrieving a customer by their ID. This method is also the one in which caching is applied.
- The version that is currently on master is the final/working version that demonstrates how the unified cache works, see "Version 4 below". There are git tags that can be use to quickly walk through the code as we evolve from no caching to the unified caching model.
- The unified caching library includes the spring boot actuator and adds a "promotion" metric to each cache. 

## Setup
- There is a docker-compose.yml file in this project that can be used to start Redis in a docker container. The example assumes docker is running on your local machine.
- I recommend using a tool like redis desktop manager to connect to your redis instance to examine how the keys and objects are being stored.
- Postman is another tool that makes it convenient when calling the various rest endpoints in the examples and the file "Caching Samples.postman_collection.json" can be imported into postman.
- This project uses maven and can be built via "mvn clean package"
- The instructions walk you through the scenarios via the command line, but I highly recommend importing this project into your favorite IDE as this will make it easy to look at the code. 

## Version 1 - Running the application without any caching in place.

1. Checkout the tag "no-cache" via "git checkout tags/no-cache". 
2. Build the project "mvn clean install package"
3. Launch exampleV1, from the root of the project: "java -jar examplev1/target/examplev1-0.0.1-SNAPSHOT.jar"
4. Use your browser or postman to make the a rest call on "http://localhost:8081/customers/2". This should take a little more than 5 seconds (each time it is called) due to the artificial delay.
5. Explore the code under "exmaplev1", it is the only code in play at the moment. It is a simple Spring Boot Rest application using an in-memory "DAO" to store and get customers.

## Version 2 - Running the application with Spring's default caching.

1. Checkout the tag "simple-cache" via "git checkout tags/simple-cache".
2. Startup redis via docker : "docker-compose up" from the root of this project. 
3. Build the project "mvn clean install package"
4. Launch exampleV1, from the root of the project: "java -jar examplev1/target/examplev1-0.0.1-SNAPSHOT.jar"
5. Launch exampleV2, from the root of the project: "java -jar examplev2/target/examplev2-0.0.1-SNAPSHOT.jar"
6. Use your browser or postman to make the a rest call on "http://localhost:8081/customers/2". This should take a little more than 5 seconds (the first time) due to the artificial delay and then the cache will make the second call much quicker.
7. Use a redis client (like Redis Desktop Manager) to connect to your local redis instance. You will see a customer cache with a single entry in. 
8. Use your browser or postman to make the a rest call to second version of the application via "http://localhost:8082/customers/2". This will fail with an error due to a null pointer exception.
9. You can "flush" the cache by removing the cached value via your redis client or

You can use Postman to do a "save" on example1 by doing an HTTP POST on http://localhost:8081/customers with a request body of:

```json
    {
        "customerId": 2,
        "email": "vanilla@underpressure.com",
        "lastName": "Van Winkle",
        "firstName": "Robert"
    }
```

You can use Postman to do a "save" on example2 by doing an HTTP POST on http://localhost:8082/customers with a request body of:

```json
{
    "customerId": 2,
    "email": "vanilla@underpressure.com",
    "lastName": "Van Winkle",
    "firstName": "Robert",
    "address": {
        "address1": "123 Circle Drive",
        "address2": "Suite 100",
        "city": "Wellington",
        "state": "FL",
        "postalCode": "33449"
    }
}
```

10. I recommend experimenting with postman to see the nasty side-effects from sharing a common cache.

## Version 3 - Running the application with serialization verification enabled.

This is the first time that the unified-cache library is in play. Both examples do NOT have an application version set and therefore the caching library will default to "1.0.0-SNAPSHOT".

1. Checkout the tag "serializer-validating-cache" via "git checkout tags/serializer-validating-cache".
2. Startup redis via docker : "docker-compose up" from the root of this project. 
3. Build the project "mvn clean install package"
4. Launch exampleV1, from the root of the project: "java -jar examplev1/target/examplev1-0.0.1-SNAPSHOT.jar"
5. Launch exampleV2, from the root of the project: "java -jar examplev2/target/examplev2-0.0.1-SNAPSHOT.jar"
6. Verify there are no cached values in Redis from the previous examples.
7. Use your browser or postman to make the a rest call on "http://localhost:8081/customers/2". This should take a little more than 5 seconds (the first time) due to the artificial delay and then the cache will make the second call much quicker.
7. Use a redis client (like Redis Desktop Manager) to connect to your local redis instance. You will see a customer cache with a single entry, but now the value will have an addition "row" of data where the key is "1.0.0-SNAPSHOT" and the value is the expected serialized data. 
8. Use your browser or postman to make the a rest call to second version of the application via "http://localhost:8082/customers/2". This will no longer fail...but it will take 5 seconds as version 2 evicts version 1's copy of the cached value.
9. It you alternate between 1 and 2, you will find each time the operation takes 5 seconds...you are witnessing cache thrashing.

## Version 4 - Running the application with the unified caching model.

1. Checkout the master tip, which now sets the versions for each example to be different.
2. Startup redis via docker : "docker-compose up" from the root of this project. 
3. Build the project "mvn clean install package"
4. Launch exampleV1, from the root of the project: "java -jar examplev1/target/examplev1-0.0.1-SNAPSHOT.jar"
5. Launch exampleV2, from the root of the project: "java -jar examplev2/target/examplev2-0.0.1-SNAPSHOT.jar"
6. Verify there are no cached values in Redis from the previous examples.
7. Use your browser or postman to make the a rest call on "http://localhost:8081/customers/2". This should take a little more than 5 seconds (the first time) due to the artificial delay and then the cache will make the second call much quicker.
8. Use your browser or postman to make the a rest call to second version of the application via "http://localhost:8082/customers/2". This will take 5 seconds on the first call but the second call will will use the cached value.
9. It you alternate between 1 and 2, you will find they are now both using their own, separate copies of the "same" object from cache.
10. Use a redis client (like Redis Desktop Manager) to connect to your local redis instance. You will see a customer cache with a single entry, but now the value will two cached "rows". Each application version will show up as a key with the serialized data as the value.

### Demonstration of how promotion works.

11. Stop the application exampleV2 and now increment it's application version by editing "exampleV2/src/main/resources/application.yml" and change info.build.version to "1003".
12. Rebuild exampleV2: from the /exampleV2 folder : "mvn clean package"
13. Launch exampleV2, from the root of the project: "java -jar examplev2/target/examplev2-0.0.1-SNAPSHOT.jar"
14. Use your browser or postman to make the a rest call to exampleV2 of the application via "http://localhost:8082/customers/2". This call will use the cache!
15. Use a redis client (like Redis Desktop Manager) to connect to your local redis instance. You will see a customer cache with a single entry, but now the value will three cached "rows". Each application version (Including 1003) will show up as a key with the serialized data as the value. The previous version of the object was promoted to the new version without having to make a call to the database.
