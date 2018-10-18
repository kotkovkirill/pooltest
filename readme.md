ResourcePool is implemented on 2 non thread safe hashsets and two types of locks:
- global lock with condition responsible for locking acquire method
- internal lock on each resource object passed to remove method responsible for locking remove method

I decided not to use linkedblockingqueue due to the fact that requirements could not be implemented with one collection and we still need additional storage for "used" resources

There is a bunch of tests prepared in PoolImplTest.java, main one is testConcurrency, where thread safety of the class is checked


Possible ways to improve solution:
- introduce waiting fairness
- use threadlocals to improve performance if this resource pool is indended to use in typical scenario resource per thread (as for example connection pool)
