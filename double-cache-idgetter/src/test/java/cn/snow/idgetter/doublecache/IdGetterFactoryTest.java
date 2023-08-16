package cn.snow.idgetter.doublecache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

class IdGetterFactoryTest {

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
    }

    Map<String, Long> mockDbTable = new ConcurrentHashMap<>();


    @Test
    void testIdGetterByMySqlSaveMaxId(){

        IdGetterFactory factory = new IdGetterFactory(new ISequenceRepository() {
            @Override
            public Long getCurrentSequence(String bizTag) {
                return mockDbTable.getOrDefault(bizTag, 0L);
            }

            @Override
            public boolean increaseSequence(String bizTag, long incrSize, Long currentSequence) {
                if(mockDbTable.getOrDefault(bizTag, 0L) == currentSequence ){
                    mockDbTable.put(bizTag, incrSize + currentSequence);
                    return true;
                }
                return false;
            }
        }, 10);

        for (int i = 0; i < 50; i++) {
            factory.getId("tableName1");
        }

    }
    @Test
    void testIdGetterByPgSequenceMultiIncrSize(){
        IdGetterImplBySequence idGetter = new IdGetterImplBySequence("tableName2", 20L, new ISequenceRepository() {
            @Override
            public Long getCurrentSequence(String bizTag) {
                mockDbTable.put(bizTag, mockDbTable.getOrDefault(bizTag, 0L) + 1);
                return mockDbTable.get(bizTag);
            }

            @Override
            public boolean increaseSequence(String bizTag, long incrSize, Long currentSequence) {
                return false;
            }

        });

        for (int i = 0; i < 50; i++) {
            idGetter.getId();
        }
    }
    @Test
    void testIdGetterByMySqlSaveMaxIdGroupByDate(){

    }
}