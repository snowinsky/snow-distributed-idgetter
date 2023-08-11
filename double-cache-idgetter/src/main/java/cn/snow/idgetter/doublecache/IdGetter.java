package cn.snow.idgetter.doublecache;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.ReentrantLock;

import lombok.extern.slf4j.Slf4j;

/**
 * ID的生产者，级别到bizTag级别, 也就是到每一张表
 * 期望的结果是，获取ID这件事不依赖原有事务
 *
 * @author dev
 */
@Slf4j
public class IdGetter {
    /**
     * 为这个表的ID准备两个ID备用池，互为主备。
     * 这个对象也是IdGetter这个类的最重要的操作对象
     * 双缓存
     */
    private final AtomicReferenceArray<IdSegment> segment;
    /**
     * 锁，为了保证一个实例一把锁
     */
    private final ReentrantLock lock;
    /**
     * 异步加载第二个缓存的异步任务
     */
    private AtomicReference<FutureTask<Boolean>> asyncLoadSegmentTask;
    /**
     * 表名
     */
    private final String bizTag;
    /**
     * 每次拿取的id的数量
     */
    private final Long incrSize;
    /**
     * 是异步加载第二个缓存吗？ true=异步 false=同步
     */
    private boolean asyncLoadingSegment;
    /**
     * id仓库
     */
    private final ISequenceRepository sequenceRepository;
    /**
     * 如果asyncLoadingSegment=true，则需要设置这一项
     */
    private ExecutorService taskExecutor;
    /**
     * 双缓存切换，是否需要切换缓冲区
     */
    private volatile boolean segmentChanged;
    /**
     * 当前的ID
     */
    private AtomicLong currentId;

    /**
     * 同步加载第二个缓冲器
     *
     * @param tableName
     * @param increaseIdSize
     * @param sequenceRepository
     */
    public IdGetter(String tableName, Long increaseIdSize, ISequenceRepository sequenceRepository) {
        asyncLoadingSegment = false;
        segment = new AtomicReferenceArray<>(2);
        bizTag = tableName;
        incrSize = increaseIdSize == null ? 5000L : increaseIdSize;
        this.sequenceRepository = sequenceRepository;
        lock = new ReentrantLock();
        init();
    }

    /**
     * 异步加载第二个缓冲区
     *
     * @param tableName
     * @param increaseIdSize
     * @param sequenceRepository
     * @param taskExecutor
     */
    public IdGetter(String tableName, Long increaseIdSize, ISequenceRepository sequenceRepository, ExecutorService taskExecutor) {
        this(tableName, increaseIdSize, sequenceRepository);
        asyncLoadingSegment = true;
        this.taskExecutor = taskExecutor;
        asyncLoadSegmentTask = new AtomicReference<>();
        init();
    }

    /**
     * 读取当前最大值，然后填充缓冲区，并初始化递增ID值
     */
    public void init() {
        segment.set(0, loadOtherSegment(bizTag));
        setSegmentChanged(false);
        currentId = new AtomicLong(segment.get(currentSegmentIndex()).getMinId());
    }

    private boolean isSegmentChanged() {
        return segmentChanged;
    }

    private void setSegmentChanged(boolean isSegmentChanged) {
        segmentChanged = isSegmentChanged;
    }

    /**
     * 异步获取数据装填缓冲区，主要是装填备用缓冲区
     *
     * @return
     */
    private Long asyncGetId() {
        lock.lock();
        try {
            //当前缓冲区使用超过50%，则需要加载另一个缓冲区, 不同之处在于加载另一个缓冲区是异步完成
            if (needLoadOtherSegment() && asyncLoadSegmentTask.get() == null) {
                asyncLoadOtherSegment();
            }
            //当前缓冲区使用量达100%，切换到另一个缓冲区。
            if (needSwitchToOtherSegment()) {
                asyncSwitchOtherSegment();
            }
            return currentId.incrementAndGet();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 异步切换到备用缓冲区
     * 切换之前得确保填充备用缓冲区的动作已经完成且装填成功
     */
    private void asyncSwitchOtherSegment() {
        if (needSwitchToOtherSegment()) {
            boolean isLoadingSuccess;
            try {
                isLoadingSuccess = (asyncLoadSegmentTask.get() == null || asyncLoadSegmentTask.get().get(1500, TimeUnit.MILLISECONDS));
                // 确保另一个缓冲区已加载结束
                if (isLoadingSuccess) {
                    setSegmentChanged(!isSegmentChanged());
                    currentId = new AtomicLong(segment.get(currentSegmentIndex()).getMinId());
                    asyncLoadSegmentTask.set(null);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("get the asyncLoadSegmentTask result InterruptedException fail", e);
                isLoadingSuccess = false;
                asyncLoadSegmentTask.set(null);
            } catch (ExecutionException e) {
                log.warn("get the asyncLoadSegmentTask result ExecutionException fail", e);
                isLoadingSuccess = false;
                asyncLoadSegmentTask.set(null);
            } catch (TimeoutException e) {
                log.warn("get the asyncLoadSegmentTask result TimeoutException fail", e);
                isLoadingSuccess = false;
                asyncLoadSegmentTask.get().cancel(false);
                asyncLoadSegmentTask.set(null);
            }
            //如果备用缓冲区没有装填成功，那么就只能是无限循环，直到主缓冲区装填完毕
            if (!isLoadingSuccess) {
                doUntilFillOtherSegmentSuccess();
                setSegmentChanged(!isSegmentChanged());
                currentId = new AtomicLong(segment.get(currentSegmentIndex()).getMinId());
            }
        }
    }

    private void doUntilFillOtherSegmentSuccess() {
        int tryTime = 0;
        while (isOtherSegmentEmpty()) {
            if (tryTime++ > 50) {
                throw new IdGetFatalException("try to fill Other segment fail over limit.....");
            }
            try {
                IdGetterThreadPool.delayExecutePool().schedule(()->{
                    try {
                        segment.set(otherSegmentIndex(), loadOtherSegment(bizTag));
                    } catch (IdGetFailException e) {
                        log.warn("The IdGetFailException can be retry until the other segment full", e);
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException interruptedException) {
                            log.warn("doUntilFillOtherSegmentSuccess sleep InterruptedException", e);
                            Thread.currentThread().interrupt();
                        }
                    }
                }, 1000, TimeUnit.MILLISECONDS).get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                throw new IdGetFailException("load id segment fail", e);
            }

        }
    }

    /**
     * 异步装填备用缓冲区
     * 将更新数据库的任务设置为异步
     */
    private void asyncLoadOtherSegment() {
        if (needLoadOtherSegment()) {
            FutureTask<Boolean> f = new FutureTask<>(() -> {
                segment.set(otherSegmentIndex(), loadOtherSegment(bizTag));
                return true;
            });
            asyncLoadSegmentTask.set(f);
            taskExecutor.submit(f);
        }
    }

    /**
     * 判断备用缓冲区是否需要装填
     * 如果备用缓冲区的初始值还比主缓冲区的初始值小，那就是没更新，也需要更新了
     *
     * @return
     */
    private boolean isOtherSegmentEmpty() {
        if (segment.get(otherSegmentIndex()) == null) {
            return true;
        }
        return segment.get(otherSegmentIndex()).getMinId() < segment.get(currentSegmentIndex()).getMinId();
    }

    /**
     * 同步获取ID
     * 需要更新备用缓冲区时，同步等待备用缓冲区更新完，然后判断是否需要切换缓冲区，需要切换则同步切换完成，然后才获取ID
     *
     * @return
     */
    private synchronized long syncGetId() {
        lock.lock();
        try {
            //当前缓冲区使用超过50%，则需要加载另一个缓冲区
            if (needLoadOtherSegment()) {
                syncLoadOtherSegment();
            }
            //当前缓冲区使用量达100%，切换到另一个缓冲区
            if (needSwitchToOtherSegment()) {
                syncSwitchOtherSegment();
            }
            return currentId.incrementAndGet();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 同步切换备用缓冲区
     */
    private void syncSwitchOtherSegment() {
        if (needSwitchToOtherSegment()) {
            // 如果另一个缓冲区也是空的，就不得不加载,直到成功
            doUntilFillOtherSegmentSuccess();
            // 确保另一个缓冲区已加载后，切换缓冲区到另一个
            setSegmentChanged(!isSegmentChanged());
            currentId = new AtomicLong(segment.get(currentSegmentIndex()).getMinId());
        }
    }

    /**
     * 同步加载备用缓冲区
     */
    private void syncLoadOtherSegment() {
        if (needLoadOtherSegment()) {
            // 使用50%以上，并且没有加载成功过，就进行加载
            segment.set(otherSegmentIndex(), loadOtherSegment(bizTag));
        }
    }

    /**
     * 是否需要切换备用缓冲区
     * 条件是当前值已经大于等于主缓冲区的最大值
     *
     * @return
     */
    private boolean needSwitchToOtherSegment() {
        return segment.get(currentSegmentIndex()).getMaxId() <= currentId.longValue();
    }

    /**
     * 是否需要加载备用缓冲区
     * 当前值已经大于主缓冲区中间值时，并且备用缓冲区是空
     *
     * @return
     */
    private boolean needLoadOtherSegment() {
        return segment.get(currentSegmentIndex()).getMiddleId() <= currentId.longValue() && isOtherSegmentEmpty();
    }

    /**
     * 获取下一个ID
     * <p>
     * IdGetter的主方法，如果有接口的话，这是接口中的唯一方法
     *
     * @return
     */
    public Long getId() {
        Long nextId = asyncLoadingSegment ? asyncGetId() : syncGetId();
        log.info("####### current segment={}, willReturnId={}", segment, nextId);
        return nextId;
    }

    /**
     * 获取主缓冲区索引，知道从哪个缓存里取值
     *
     * @return
     */
    private int currentSegmentIndex() {
        return isSegmentChanged() ? 1 : 0;
    }

    /**
     * 获取备用缓冲区的索引，两个缓冲区互为主备。因此根据切换状态的true false，自动切换
     *
     * @return
     */
    private int otherSegmentIndex() {
        return isSegmentChanged() ? 0 : 1;
    }

    /**
     * 加载备用缓冲区，或者说只是加载一个缓冲区
     *
     * @param bizTag
     * @return
     */
    private IdSegment loadOtherSegment(String bizTag) {
        for (int i = 0; i < 20; i++) {
            try {
                return updateId(bizTag);
            } catch (Exception e) {
                log.warn("load id segment fail, it will be re-try", e);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException interruptedException) {
                    log.warn("load id segment={} sleep interrupted", bizTag, interruptedException);
                    Thread.currentThread().interrupt();
                }
            }
        }
        throw new IdGetFailException("load id segment fail and over the re-try time");
    }

    /**
     * 获取主缓冲区
     *
     * @param bizTag
     * @return
     */
    private IdSegment updateId(String bizTag) {
        try {
            log.info("start to get batch ids from repository for {}", bizTag);
            final long currentValue = sequenceRepository.getCurrentSequence(bizTag);
            log.info("get current max value from repository for={} return={}", bizTag, currentValue);
            if (sequenceRepository.increaseSequence(bizTag, incrSize, currentValue)) {

                final IdSegment currentSegment = new IdSegment();
                currentSegment.setMaxId(currentValue);
                currentSegment.setStep(incrSize);
                Long newMaxId = currentSegment.getMaxId() + currentSegment.getStep();

                IdSegment newSegment = new IdSegment();
                newSegment.setStep(currentSegment.getStep());
                newSegment.setMaxId(newMaxId);

                log.info("get batch ids from repository for {} success. the result={}", bizTag, newSegment);
                return newSegment;
            } else {
                throw new IdGetFailException("increaseSequence return false bizTag=" + bizTag);
            }
        } catch (IdGetFailException e) {
            throw e;
        } catch (Exception e) {
            throw new IdGetFatalException("updateId fail. bizTag=" + bizTag, e);
        }
    }
}
