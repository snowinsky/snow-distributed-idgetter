package cn.snow.idgetter.doublecache;

public class SequenceRepositoryImplBySequence implements ISequenceRepository {

    @Override
    public Long getCurrentSequence(String bizTag) {
        //TODO getNextSequence
        return null;
    }

    @Override
    public boolean increaseSequence(String bizTag, long incrSize, Long currentSequence) {
        throw new UnsupportedOperationException("getCurrentSequence is increaseOneAndGet, so the increaseSequence was not required");
    }

}
