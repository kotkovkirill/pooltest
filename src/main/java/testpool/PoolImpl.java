package testpool;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class PoolImpl<R> implements Pool<R> {

    private final Set<R> freeResources = new HashSet<>();
    private final Set<R> usedResources = new HashSet<>();
    private final ReentrantLock globalLock = new ReentrantLock();
    private Condition resourceAvailableCondition = globalLock.newCondition();
    private AtomicBoolean open = new AtomicBoolean(false);

    @Override
    public void open() {
        open.set(true);
    }

    @Override
    public boolean isOpen() {
        return open.get();
    }

    @Override
    public R acquire() throws InterruptedException {

        if(!open.get()) return null;

        globalLock.lock();

        try {

            while(freeResources.isEmpty())
                resourceAvailableCondition.await();

            return acquireInternal();

        } finally {
            globalLock.unlock();
        }
    }

    protected R acquireInternal() {
        R r = freeResources.iterator().next();

        freeResources.remove(r);
        usedResources.add(r);
        return r;
    }

    @Override
    public R acquire(long timeout, TimeUnit timeUnit) throws InterruptedException {

        globalLock.lock();

        try {
            while(freeResources.isEmpty())
                //return null in case of timeout
                if(!resourceAvailableCondition.await(timeout, timeUnit)) return null;

            return acquireInternal();

        } finally {
            globalLock.unlock();
        }
    }

    @Override
    public void release(R resource) {

        if(resource == null) throw new RuntimeException("Resource is null");

        synchronized (resource) {

            if(!usedResources.contains(resource)) return;

            globalLock.lock();

            try {
                if(!usedResources.contains(resource)) return;

                releaseInternal(resource);

            } finally {
                globalLock.unlock();
            }

            resource.notifyAll();
        }
    }

    protected void releaseInternal(R resource) {
        usedResources.remove(resource);
        freeResources.add(resource);
        resourceAvailableCondition.signal();
    }

    @Override
    public boolean add(R resource) {

        if(resource == null) return false;
        if(freeResources.contains(resource) || usedResources.contains(resource)) return false;

        globalLock.lock();

        try {

            if(freeResources.contains(resource) || usedResources.contains(resource)) return false;

            freeResources.add(resource);
            resourceAvailableCondition.signal();

            return true;
        } finally {
            globalLock.unlock();
        }
    }

    @Override
    public boolean remove(R resource) throws InterruptedException {

        if(resource == null) return false;
        if(!freeResources.contains(resource) && !usedResources.contains(resource)) return false;

        synchronized (resource) {
            while(usedResources.contains(resource))
                resource.wait();

            globalLock.lock();

            if(!freeResources.contains(resource) && !usedResources.contains(resource)) return false;

            try {
                usedResources.remove(resource);
                freeResources.remove(resource);
                return true;

            } finally {
                globalLock.unlock();
            }
        }
    }

    @Override
    public boolean removeNow(R resource) {
        if(resource == null) return false;
        if(!freeResources.contains(resource) && !usedResources.contains(resource)) return false;

        globalLock.lock();

        try {
            if(!freeResources.contains(resource) && !usedResources.contains(resource)) return false;

            freeResources.remove(resource);
            usedResources.remove(resource);

            return true;

        } finally {
            globalLock.unlock();
        }
    }

    @Override
    public void close() {

    }
}
