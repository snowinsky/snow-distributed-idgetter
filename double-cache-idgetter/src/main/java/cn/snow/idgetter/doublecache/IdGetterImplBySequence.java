package cn.snow.idgetter.doublecache;

import java.util.concurrent.ExecutorService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IdGetterImplBySequence extends IdGetter {


    public IdGetterImplBySequence(String tableName, Long increaseIdSize, ISequenceRepository sequenceRepository) {
        super(tableName, increaseIdSize, sequenceRepository);
    }

    public IdGetterImplBySequence(String tableName, Long increaseIdSize, ISequenceRepository sequenceRepository, ExecutorService taskExecutor) {
        super(tableName, increaseIdSize, sequenceRepository, taskExecutor);
    }

    @Override
    protected IdSegment updateId(String bizTag) {
        try {
            Long nextSeq = getSequenceRepository().getCurrentSequence(bizTag);
            IdSegment newSegment = new IdSegment();
            newSegment.setStep(getIncrSize());
            newSegment.setMaxId(nextSeq * getIncrSize());

            log.info("get batch ids from repository for {} success. the result={}", bizTag, newSegment);
            return newSegment;
        } catch (Exception e) {
            throw new IdGetFatalException("updateId fail. bizTag=" + bizTag, e);
        }
    }

}
