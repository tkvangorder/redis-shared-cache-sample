# Evolving Your Distrubuted Cache In a Continuous Delivery World

This project contains a demonstration of how the Spring caching abstraction can be customized to allow multiple versions of an application to "share" the same distributed Redis cache even when the structure of the values has changed between those versions of the software. This project is structured in such a way that it will walk through the various problems you will encounter when sharing a distributed cache.

## What do we mean by "continuous delivery"?

In a high volume system it is very common to have a cluster of nodes that has been scaled out horizontally to handle the traffic. This conceptually looks like the same application/service that has been cloned N number of times to perform the same function. The use of load balancers and circuit breakers can be applied to the system to distribute/shape traffic across the cluster and you want this system to remain online 24/7.

A continuous delivery model is a mechanism by which you upgrade the cluster to a new version of the software without ever taking the system completely offline. This can be achieved by "dropping" one or two nodes from the cluster, updating those nodes, and then bring them back on line. This process is repeated until all nodes have been updated to the new version of the software. This "rolling deployment" means that for a period of time you will have two versions of the application that are running concurrently. This means that you have to take care in that you do not introduce a breaking change into your system.

## What constitutes a breaking change?

Any time you change the structure of your domain model, there is a potential for a breaking change. 

- Renaming or renaming an attribute in your model (or column in your database)
- Adding attributes to your model. (Wait, what? How does an additive change impact existing software?)

## What does a distributed cache have to do with this?

A distributed cache is a server or collection of servers that sits in your infrastructure and provides a mechanism to store and retrieve data that is stored in memory within the cache. Accessing a cached value can be significantly faster than retrieving the value from an underlying database that may need to access that data from disk, use indexes, and possibly join entities (in a relational model). A distributed cache works by using a unique key (derived from the model you are going to cachce) and then serializing that data into the distributed cache.

It turns out that model changes can cause some havoc when you have two different versions of an application that are using the same cache.

  - An existing version of the software might cache a value under a key that does NOT include a new attribute, the new version might grab this cached value expecting the new attribute to be populated. This can lead to things "bad things" and what is worse, after the item is evicted, the error will magically go away. It's like chasing ghosts.
  - What if you build in a solution such that as you deserialize a value from cache that you can verify its structure matches? This works better, as you dont end up getting errors, but can lead to "cache thrashing".
  1. Version A caches value.
  2. Version B gets the value, fails to serialize, evicts that value from the cache, retrieves its version of the model  and then puts that in the cache.
  3. Version A gets the value, fails to serialize, evicts that value from the cache, retrieves its version of the model  and then puts that in the cache.
 
## How do you solve this?

One way to solve the problems with caching is to provision a new redis server for each new version of the software. The obvious downside of this approach is that the cache will be empty initially and will require a warm-up period.

## A BETTER WAY:

In Redis, it is possible to store a HashSet as a value under a specific key. This means that for a given key, you can store multiple copies of the cached data using a more specific key and it is possible to use the application version as the secondary hash key under which to store the serialized data. This means that each version of the applicaiton will have its own copy of the cached value. If you combine this with the ability to detect model changes during the deserialization process, you can promote compatible versions of a cached value from one version to the next!

This implentation relies on two customizations: 

1. A customer serializer is used to convert the cached model to JSON. This serializer will encode a model's serialVersionUID into the JSON stream. The deserializer will compare the serialVersionUID stored in the cache with the version in the application. This approach is recursive and any sub-elements are also encoded in the same way. If a version in cache does not match a version in the application, the deserializer will fail. This requires that each cached model implements "Serializable" and that each time the model is altered, a developer must increment the serialVersionUID.

2. The cache implementation has been altered to use a hashset as the value, where the key in the hashset is the application's build version. This relies on the deployment process to increment and inject the version into the application. This also means that each version of the application will have its own copy of a cached value. This ends up looking like <CACHE_NAME> <UNIQUE KEY> <APPLICATION VERSION> ---> Cached Value.


### Cache Promotion

If there is a cache "miss" for a specific version of the application, the cache implementation will attempt to find the most recent, previous version's cached value. If the deserialization process succeeds, we know the model stored in cache is compatible the version expected by the application. This version is promoted and put into the new version's cache and does not require the new version to resolve the value against the underlying data store.


## Project organization

There are three projects in this library: 

- examplev1 This is a simple Spring boot application that provides rest endpoints to save/get customers.
- examplev2 This is a duplicate of the first application, except that the Customer Model has been evolved to include a nested Address model.
- unified-cache This is library that can be added as a dependency to any project and it will enable redis caching and the unified caching model via Spring Boot auto-configuration.

## NOTES:

- The example rest application uses an in-memory approach to data access and has an artificial 5 second delay when retrieving a customer by their ID. This method is also the one in which caching is applied.
- The version that is currently on master is the final/working version that demonstrates how the unified cache works, see "Version 4 and 4a below". There are git tags that can be use to quickly walk through the code as we evolve from no caching to the unified caching model.

## Setup
- There is a docker-compose.yml file in this project that can be used to start Redis in a docker container. The example assumes docker is running on your local machine.
- I recommend using a tool like redis desktop manager to connect to your redis instance to examine how the keys and objects are being stored.
- Postman is another tool that makes it convenient when calling the various rest endpoints in the examples and the file "Caching Samples.postman_collection.json" can be imported into postman.
- This project uses maven and can be built via "mvn clean package"

## Version 1 - Running the application without any caching in place.


## Version 2 - Running the application with Spring's default caching.

## Version 3 - Running the application with serialization verification enabled.

## Version 4 - Running the application with the unified caching model.

## Version 4a - Demonstration of how promotion works.

