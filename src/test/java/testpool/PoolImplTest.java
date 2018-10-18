package testpool;


import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

public class PoolImplTest {

    static ExecutorService executorService = Executors.newFixedThreadPool(5);

    Pool<Integer> pool;

    @Before
    public void init() {
        pool = new PoolImpl<>();
        pool.open();
    }

    @Test
    public void basicTest() throws InterruptedException {

        Integer resource = new Integer(1);

        pool.add(resource);

        Integer acquired = pool.acquire();

        Assert.assertEquals(resource, acquired);

        pool.release(acquired);

        acquired = pool.acquire();

        Assert.assertEquals(resource, acquired);
    }

    @Test
    public void basicTimeoutTest() throws InterruptedException {

        Integer resource = new Integer(1);

        pool.add(resource);

        Integer acquired = pool.acquire(1, TimeUnit.SECONDS);

        Assert.assertEquals(resource, acquired);

        pool.release(acquired);

        acquired = pool.acquire(1, TimeUnit.SECONDS);

        Assert.assertEquals(resource, acquired);
    }

    @Test
    public void testAcquireTimeout() throws InterruptedException {
        Integer resource = pool.acquire(1, TimeUnit.SECONDS);
        Assert.assertNull(resource);
    }

    @Test
    public void testAdd() {
        Integer resource = new Integer(1);
        Assert.assertTrue(pool.add(resource));
        Assert.assertFalse(pool.add(resource));
    }

    @Test
    public void testRemove() throws InterruptedException {
        Assert.assertFalse(pool.remove(null));
        Assert.assertFalse(pool.remove(new Integer(1)));
        Integer r = new Integer(1);
        pool.add(r);
        Assert.assertTrue(pool.remove(r));
    }

    @Test
    public void testBlockingBehaviourNull() {

        AtomicReference<Integer> resource = new AtomicReference<>();

        runWithTimeout(1, () -> {
            try {
                resource.set(pool.acquire());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        Assert.assertNull(resource.get());
    }

    @Test
    public void testBlockingBehaviourResult() {

        final Integer r = new Integer(1);

        //add resource after 3 sec
        executorService.execute(() -> {
            try {
                Thread.sleep(3000);
                pool.add(r);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        //wait 10 secs till the resource will be available
        long start = System.currentTimeMillis();
        AtomicReference<Integer> resource = new AtomicReference<>();
        runWithTimeout(10, () -> {
            try {
                resource.set(pool.acquire());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        Assert.assertEquals(resource.get(), r);
        //assert we waited more than 2 seconds
        Assert.assertTrue((System.currentTimeMillis() - start) > 2000);
    }



    final Map<Integer, AtomicInteger> statistic = new ConcurrentHashMap<>();
    final Pool<Integer> testablePool = new PoolImpl<Integer>() {
        @Override
        protected Integer acquireInternal() {
            Integer res = super.acquireInternal();
            System.out.println("acquired " + res);
            statistic.get(res).incrementAndGet();
            return res;
        }
        @Override
        protected void releaseInternal(Integer resource) {
            statistic.get(resource).decrementAndGet();
            System.out.println("released " + resource);
            super.releaseInternal(resource);
        }
    };
    AtomicReference<Long> stopTime = new AtomicReference<>();

    @Test
    public void testConcurrency() throws InterruptedException {

        IntStream.range(0, 5).forEach(i -> {
            statistic.put(i, new AtomicInteger(0));
            testablePool.add(i);
        });

        ExecutorService threads = Executors.newFixedThreadPool(10);

        List<Callable<Object>> tasks = new ArrayList<>();

        Callable<Object> acquireTask = Executors.callable(() -> {
            IntStream.range(0, 10000).forEach( (i) -> {
                try {
                    testablePool.acquire();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
            System.out.println("end");
            stopTime.set(System.currentTimeMillis());

        });

        IntStream.range(0, 5).forEach(i -> {
            tasks.add(acquireTask);
            tasks.add( releaseTask(i));
        });

        threads.invokeAll(tasks);

        //check test results correctness
        statistic.forEach((i, v) -> {
            System.out.println(i + " " + v);
            Assert.assertEquals(v.get(), 0);
        });
    }

    private Callable<Object> releaseTask(final Integer i) {
        return () -> {
            //release for 3 seconds after acquiring threads finished
            while(stopTime.get() == null || (System.currentTimeMillis() - stopTime.get()) < 3000)
                testablePool.release(i);
            return null;
        };
    }

    public static void runWithTimeout(long seconds, Runnable r) {

        Future task = executorService.submit(r);

        try {
            task.get(seconds, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {

        }

    }
}
