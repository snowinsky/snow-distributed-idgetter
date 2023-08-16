# snow-idgetter

> 很简单的双缓冲区的唯一ID生成器，其中cn.snow.idgetter.doublecache.IdGetterFactory是唯一的对外接口
>
> 需要依赖数据库，建表语句在sql文件夹里

### 常用的唯一id生成方式(咱说的是数字型的，不说雪花算法和uuid之类的)

1. Oracle或者PG的sequence
2. Mysql的自增ID
3. redis的INCR
4. zookeeper生成id

### 常用唯一id的场景

1. 全局唯一，比如唯一的流水号
2. 一定范围内的唯一，例如当天唯一

### 有趣的实践

- 实现上，redis可能最简单
- 高并发下，对数据库依赖太大容易出问题
- 一次拿一批，放内存里慢慢用，这种可以减少访问数据库或者redis的次数
  - 可以在数据库里记录一下最新id数，然后把这批id放内存里
  - 可以用sequence，自增id或者redis的INCR，增一个id，后面乘以10,100,1000,10000等量级数，这样也比较省事
