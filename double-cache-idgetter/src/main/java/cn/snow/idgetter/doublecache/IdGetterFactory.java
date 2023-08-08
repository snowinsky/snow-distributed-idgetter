package cn.snow.idgetter.doublecache;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Resource;

import lombok.Setter;

public class IdGetterFactory {

    public static final String T_SIN_HCC_TRX_ID = "HCC_TRX_ID";

    @Setter
    private Long sinHccTrxIdIncreaseSize;

    private static final ExecutorService THREAD_POOL = new ThreadPoolExecutor(5, 20,
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(16), new SnowIdGetterThreadFactory("snow-id-getter", true), new ThreadPoolExecutor.CallerRunsPolicy());

    private static final ConcurrentHashMap<String, IdGetter> BIZ_TAG_ID_LEAF = new ConcurrentHashMap<>();

    @Resource
    @Setter
    private ISequenceRepository sequenceRepository;

    protected Long getIdByTableName(String tableName) {
        if (BIZ_TAG_ID_LEAF.get(tableName) == null) {
            synchronized (BIZ_TAG_ID_LEAF) {
                if (BIZ_TAG_ID_LEAF.get(tableName) == null) {
                    IdGetter idGetter = new IdGetter(tableName, sinHccTrxIdIncreaseSize, sequenceRepository, THREAD_POOL);
                    BIZ_TAG_ID_LEAF.putIfAbsent(tableName, idGetter);
                }
            }
        }
        return BIZ_TAG_ID_LEAF.get(tableName).getId();
    }

    public Long getHccTrxIdCore() {
        return getIdByTableName(T_SIN_HCC_TRX_ID);
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
