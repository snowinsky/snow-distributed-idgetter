package cn.snow.idgetter.doublecache;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class IdGetterThreadPool {

    private IdGetterThreadPool(){}

    public static ScheduledExecutorService delayExecutePool() {
        ScheduledThreadPoolExecutor searchDelayPool = new ScheduledThreadPoolExecutor(
                4
                , new IdGetterFactory.SnowIdGetterThreadFactory("snow-id-getter-delay", false)
                , new ThreadPoolExecutor.CallerRunsPolicy()) {
            @Override
            public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
                if (getActiveCount() >= 5 * 10) {
                    super.getRejectedExecutionHandler().rejectedExecution(command, this);
                }
                return super.schedule(command, delay, unit);
            }
        };
        searchDelayPool.setMaximumPoolSize(10);
        return searchDelayPool;
    }

    @SuppressWarnings("all")
    public static class SnowIdGetterThreadFactory implements ThreadFactory {

        protected static final AtomicInteger POOL_SEQ = new AtomicInteger(1);

        protected final AtomicInteger mThreadNum = new AtomicInteger(1);

        protected final String mPrefix;

        protected final boolean mDaemon;

        protected final ThreadGroup mGroup;

        public SnowIdGetterThreadFactory(String prefix, boolean daemon) {
            mPrefix = prefix + "-thread-";
            mDaemon = daemon;
            SecurityManager s = System.getSecurityManager();
            mGroup = (s == null) ? Thread.currentThread().getThreadGroup() : s.getThreadGroup();
        }

        @Override
        public Thread newThread(Runnable r) {
            String name = mPrefix + mThreadNum.getAndIncrement();
            Thread ret = new Thread(mGroup, r, name, 0);
            ret.setDaemon(mDaemon);
            return ret;
        }
    }
}
