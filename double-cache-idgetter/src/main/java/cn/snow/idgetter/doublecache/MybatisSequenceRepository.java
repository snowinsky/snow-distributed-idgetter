package cn.snow.idgetter.doublecache;

import javax.annotation.Resource;

import cn.snow.idgetter.ISequenceRepository;
import cn.snow.idgetter.doublecache.dao.TSeqConfDao;


public class MybatisSequenceRepository implements ISequenceRepository {

    @Resource
    private TSeqConfDao seqConfDao;

    @Override
    public Long getCurrentSequence(String bizTag) {
        return seqConfDao.selectSeqNum(bizTag);
    }

    @Override
    //@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class, timeout = 2)
    public boolean increaseSequence(String bizTag, long incrSize, long currentSequence) {
        return seqConfDao.updateSeqNum(bizTag, incrSize, currentSequence) == 1;
    }
}