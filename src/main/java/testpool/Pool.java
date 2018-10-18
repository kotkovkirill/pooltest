package testpool;

public interface Pool<R> {
    void open();
    boolean isOpen();
    void close();
    R acquire() throws InterruptedException;
    R acquire(long timeout, java.util.concurrent.TimeUnit timeUnit) throws InterruptedException;
    void release(R resource);
    boolean add(R resource);
    boolean remove(R resource) throws InterruptedException;
    boolean removeNow(R resource);
}
