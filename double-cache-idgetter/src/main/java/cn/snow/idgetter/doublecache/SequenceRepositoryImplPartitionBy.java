package cn.snow.idgetter.doublecache;

public class SequenceRepositoryImplPartitionBy implements ISequenceRepository{
    @Override
    public Long getCurrentSequence(String bizTag) {
        //TODO bizTag=groupBy+partitionBy
        return null;
    }

    @Override
    public boolean increaseSequence(String bizTag, long incrSize, Long currentSequence) {
        /**
         * TODO
         * 1. currentSequence == null, insert, return count == 1
         * 2. currentSequence != null, update, return count == 1
         */
        return false;
    }
}
