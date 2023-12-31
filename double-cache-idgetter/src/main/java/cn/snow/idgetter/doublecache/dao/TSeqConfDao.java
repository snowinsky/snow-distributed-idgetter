package cn.snow.idgetter.doublecache.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TSeqConfDao {

    @Update("update T_SEQ_CONF\n" +
            "        set CURRENT_VALUE = CURRENT_VALUE + #{incrSize, jdbcType=DECIMAL},\n" +
            "            edate         = now()\n" +
            "        where NAME = #{seqName, jdbcType=VARCHAR}\n" +
            "          and CURRENT_VALUE = #{currVal, jdbcType=DECIMAL}")
    int updateSeqNum(@Param("seqName") String seqName, @Param("incrSize") Long incrSize, @Param("currVal") Long currVal);

    @Select("select CURRENT_VALUE\n" +
            "        from T_SEQ_CONF\n" +
            "        where NAME = #{seqName, jdbcType=VARCHAR}\n" +
            "          and #{currentMilliSecond} > 0 " +
            "          and status = 'a'")
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    Long selectSeqNum(@Param("seqName") String seqName, @Param("currentMilliSecond") Long currentMilliSecond);
}