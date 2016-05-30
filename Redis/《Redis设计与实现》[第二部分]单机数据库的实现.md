##《Redis设计与实现》[第二部分]单机数据库的实现
##**1、数据库**
> 关键字：键空间，过期，删除策略

###数据结构源码
```
//redisServer中属性太多，篇幅限制，故只列本章描述相关的属性
struct redisServer {
    //...
    // 数据库
                //一个数组，保存着服务器中的所有数据库
    redisDb *db;
    // 服务器的数据库数量
    int dbnum;          
    //..
} ;
```
Redis服务器将所有数据库都保存在服务器状态redis.h/redisServer结构的db数组中，db数组的每个项都是一个redis.h/redisDb结构，每个redisDb结构代表一个数据库。

dbnum属性的值由服务器配置的database项决定，默认为16，所以Redis服务器默认会创建16个数据库。
```
/* Redis database representation. There are multiple databases identified
 * by integers from 0 (the default database) up to the max configured
 * database. The database number is the 'id' field in the structure. */
typedef struct redisDb {

    // 数据库键空间，保存着数据库中的所有键值对
    dict *dict;                 /* The keyspace for this DB */

    // 键的过期时间，字典的键为键，字典的值为过期事件 UNIX 时间戳
            //过期字典，保存着键的过期时间
    dict *expires;              /* Timeout of keys with a timeout set */

    // 正处于阻塞状态的键
    dict *blocking_keys;        /* Keys with clients waiting for data (BLPOP) */

    // 可以解除阻塞的键
    dict *ready_keys;           /* Blocked keys that received a PUSH */

    // 正在被 WATCH 命令监视的键
    dict *watched_keys;         /* WATCHED keys for MULTI/EXEC CAS */

    struct evictionPoolEntry *eviction_pool;    /* Eviction pool of keys */

    // 数据库号码
    int id;                     /* Database ID */

    // 数据库的键的平均 TTL ，统计信息
    long long avg_ttl;          /* Average TTL, just for stats */

} redisDb;
```
每个Redis客户端都有自己的目标数据库，客户端执行读写命令之前会先切换到目标数据库，默认为0号数据库。

客户端可以通过select命令切换目标数据库。
```
typedef struct redisClient{
    //..
    //记录客户端当前正在使用的数据库
    redisDb *db;
    //..
} redisClient;
```
db属性是一个指向redisDb结构的指针，指向redisServer.db数组的其中一个元素，记录了客户端当前的目标数据库。

目前为止，Redis中没有可以返回客户端目标数据库的命令，为免误操作，再执行写命令之前，最好先执行一个select命令，显式切换到指定数据库。
```
typedef struct redisDb{
    //..
    // 数据库键空间是一个字典，保存着数据库中的所欲键值对
    dict *dict;
    //..
}redisDb;
```
键空间和用户所见的数据库是直接对应的：

- 键空间的键就是数据库的键，每个键都是一个字符串对象
- 键空间的值就是数据库的值，每个值可以是字符串对象、列表对象、哈希表对象、集合对象和有序集合对象中的任意一种Redis对象

使用Redis命令对数据库进行读写时，服务器不仅会对键空间执行指定的读写操作，还会执行一些额外的维护操作，包括：

- 读取一个键后（读操作和写操作都要对键进行读取），服务器会根据键是否存在来更新服务器的键空间命中（hit）次数或键空间不命中（miss）次数，这两个值可以在info stats命令的keyspace_hits属性和keyspace_misses属性中查看

- 在读取一个键之后，服务器会更新键的LRU（最后一次使用）时间，该值用于计算键的闲置时间，使用Object idletime <key>命令可以查看键key的闲置时间

- 如果服务器在读取一个键时发现该键已经过期，那么服务器会先删除这个过期键，然后才执行余下的其他操作

- 如果有客户端使用watch命令监视了某个键，那么服务器在对被监视的键进行修改之后，会将这个键标记为脏（dirty），从而让事务程序注意到这个键已经被修改过
- 服务器每次修改一个键之后，都会对脏（dirty）键计数器的值增1，这个计数器会触发服务器的持久化与复制操作
- 如果服务器开启了数据库通知功能，那么在对键进行修改之后，服务器将按配置发送相应的数据库通知。

###生存与过期
通过expire与pexpire命令，客户端可以以秒或毫秒精度为数据库中的某个键设置生存时间（Time To Live，TTL），在经过指定的秒数或者毫秒数之后，服务器就会自动删除生存时间为0的键

TTL命令与PTTL命令接受一个带有生存时间或过期时间的键，返回这个键的剩余生存时间，即，返回距离这个键被服务器自动删除还有多长时间
```
typedef struct redisDb{
    //..
    // 过期字典，保存键的过期时间
    dict *expires;
    //..
}redisDb;
```
redisDb结构的expires字典保存了数据库中所有键的过期时间，即过期字典：

- 过期字典的键是一个指针，这个指针指向键空间的某个键对象（即某个数据库键）
- 过期字典的值是一个long long类型的整数，这个整数保存了键所指向的数据库键的过期时间————一个毫秒精度的UNIX时间戳

通过过期字典，检查给定键是否过期：

1. 检查给定键是否存在于过期字典，若存在，那么取得键的过期时间
2. 检查当前UNIX时间戳是否大于键的过期时间：若是，则键已过期，否则未过期

###过期键删除策略

- 定时删除：在设置键的过期时间的同时，创建一个定时器（timer），让定时器在键的过期时间来临时，立即执行对键的删除操作
    + 让服务器创建大量定时器，实现定时删除策略，占用大量CPU时间，影响服务器的响应时间和吞吐量
- 惰性删除：放任过期键不管，每次从键空间取键时，检查所取键是否过期，如果过期，删除该键，若没有过期，返回该键
    + 不主动释放过期键，会造成内存的浪费，有内存泄漏的危险
- 定期删除：每隔一段时间，程序对数据库进行依次检查，删除数据库里的过期键。
    + 难点在于确定删除操作执行的时长和频率
    
Redis服务器实际使用的是惰性删除和定期删除两种策略：
> 通过配合使用这两种删除策略，服务器可以很好的在合理使用CPU时间和避免浪费内存空间之间取得平衡。

####过期删除函数

过期键的惰性删除策略由db.c/expireIfNeeded函数实现。
```
/*
 * 检查 key 是否已经过期，如果是的话，将它从数据库中删除。
 *
 * 返回 0 表示键没有过期时间，或者键未过期。
 *
 * 返回 1 表示键已经因为过期而被删除了。
 */
int expireIfNeeded(redisDb *db, robj *key) {

    // 取出键的过期时间
    mstime_t when = getExpire(db,key);
    mstime_t now;

    // 没有过期时间
    if (when < 0) return 0; /* No expire for this key */

    /* Don't expire anything while loading. It will be done later. */
    // 如果服务器正在进行载入，那么不进行任何过期检查
    if (server.loading) return 0;

    /* If we are in the context of a Lua script, we claim that time is
     * blocked to when the Lua script started. This way a key can expire
     * only the first time it is accessed and not in the middle of the
     * script execution, making propagation to slaves / AOF consistent.
     * See issue #1525 on Github for more information. */
    now = server.lua_caller ? server.lua_time_start : mstime();

    /* If we are running in the context of a slave, return ASAP:
     * the slave key expiration is controlled by the master that will
     * send us synthesized DEL operations for expired keys.
     *
     * Still we try to return the right information to the caller, 
     * that is, 0 if we think the key should be still valid, 1 if
     * we think the key is expired at this time. */
    // 当服务器运行在 replication 模式时
    // 附属节点并不主动删除 key
    // 它只返回一个逻辑上正确的返回值
    // 真正的删除操作要等待主节点发来删除命令时才执行
    // 从而保证数据的同步
    if (server.masterhost != NULL) return now > when;

    // 运行到这里，表示键带有过期时间，并且服务器为主节点

    /* Return when this key has not expired */
    // 如果未过期，返回 0
    if (now <= when) return 0;

    /* Delete the key */
    server.stat_expiredkeys++;

    // 向 AOF 文件和附属节点传播过期信息
    propagateExpire(db,key);

    // 发送事件通知
    notifyKeyspaceEvent(REDIS_NOTIFY_EXPIRED,
        "expired",key,db->id);

    // 将过期键从数据库中删除
    return dbDelete(db,key);
}
```
所有读写数据库的Redis命令在执行之前都会调用该函数对输入键检查:

- 如果输入键已经过期，那么expireIfNeeded函数将输入键从数据库中删除
- 如果输入键未过期，那么expireIfNeeded函数不做操作

####定期删除函数
过期键的定期删除策略由redis.c/activeExpireCycle函数实现。
```
/* Try to expire a few timed out keys. The algorithm used is adaptive and
 * will use few CPU cycles if there are few expiring keys, otherwise
 * it will get more aggressive to avoid that too much memory is used by
 * keys that can be removed from the keyspace.
 *
 * 函数尝试删除数据库中已经过期的键。
 * 当带有过期时间的键比较少时，函数运行得比较保守，
 * 如果带有过期时间的键比较多，那么函数会以更积极的方式来删除过期键，
 * 从而可能地释放被过期键占用的内存。
 *
 * No more than REDIS_DBCRON_DBS_PER_CALL databases are tested at every
 * iteration.
 *
 * 每次循环中被测试的数据库数目不会超过 REDIS_DBCRON_DBS_PER_CALL 。
 *
 * This kind of call is used when Redis detects that timelimit_exit is
 * true, so there is more work to do, and we do it more incrementally from
 * the beforeSleep() function of the event loop.
 *
 * 如果 timelimit_exit 为真，那么说明还有更多删除工作要做，
 * 那么在 beforeSleep() 函数调用时，程序会再次执行这个函数。
 *
 * Expire cycle type:
 *
 * 过期循环的类型：
 *
 * If type is ACTIVE_EXPIRE_CYCLE_FAST the function will try to run a
 * "fast" expire cycle that takes no longer than EXPIRE_FAST_CYCLE_DURATION
 * microseconds, and is not repeated again before the same amount of time.
 *
 * 如果循环的类型为 ACTIVE_EXPIRE_CYCLE_FAST ，
 * 那么函数会以“快速过期”模式执行，
 * 执行的时间不会长过 EXPIRE_FAST_CYCLE_DURATION 毫秒，
 * 并且在 EXPIRE_FAST_CYCLE_DURATION 毫秒之内不会再重新执行。
 *
 * If type is ACTIVE_EXPIRE_CYCLE_SLOW, that normal expire cycle is
 * executed, where the time limit is a percentage of the REDIS_HZ period
 * as specified by the REDIS_EXPIRELOOKUPS_TIME_PERC define. 
 *
 * 如果循环的类型为 ACTIVE_EXPIRE_CYCLE_SLOW ，
 * 那么函数会以“正常过期”模式执行，
 * 函数的执行时限为 REDIS_HS 常量的一个百分比，
 * 这个百分比由 REDIS_EXPIRELOOKUPS_TIME_PERC 定义。
 */

void activeExpireCycle(int type) {
    /* This function has some global state in order to continue the work
     * incrementally across calls. */
    // 静态变量，用来累积函数连续执行时的数据
    static unsigned int current_db = 0; /* Last DB tested. */
    static int timelimit_exit = 0;      /* Time limit hit in previous call? */
    static long long last_fast_cycle = 0; /* When last fast cycle ran. */

    unsigned int j, iteration = 0;
    // 默认每次处理的数据库数量
    unsigned int dbs_per_call = REDIS_DBCRON_DBS_PER_CALL;
    // 函数开始的时间
    long long start = ustime(), timelimit;

    // 快速模式
    if (type == ACTIVE_EXPIRE_CYCLE_FAST) {
        /* Don't start a fast cycle if the previous cycle did not exited
         * for time limt. Also don't repeat a fast cycle for the same period
         * as the fast cycle total duration itself. */
        // 如果上次函数没有触发 timelimit_exit ，那么不执行处理
        if (!timelimit_exit) return;
        // 如果距离上次执行未够一定时间，那么不执行处理
        if (start < last_fast_cycle + ACTIVE_EXPIRE_CYCLE_FAST_DURATION*2) return;
        // 运行到这里，说明执行快速处理，记录当前时间
        last_fast_cycle = start;
    }

    /* We usually should test REDIS_DBCRON_DBS_PER_CALL per iteration, with
     * two exceptions:
     *
     * 一般情况下，函数只处理 REDIS_DBCRON_DBS_PER_CALL 个数据库，
     * 除非：
     *
     * 1) Don't test more DBs than we have.
     *    当前数据库的数量小于 REDIS_DBCRON_DBS_PER_CALL
     * 2) If last time we hit the time limit, we want to scan all DBs
     * in this iteration, as there is work to do in some DB and we don't want
     * expired keys to use memory for too much time. 
     *     如果上次处理遇到了时间上限，那么这次需要对所有数据库进行扫描，
     *     这可以避免过多的过期键占用空间
     */
    if (dbs_per_call > server.dbnum || timelimit_exit)
        dbs_per_call = server.dbnum;

    /* We can use at max ACTIVE_EXPIRE_CYCLE_SLOW_TIME_PERC percentage of CPU time
     * per iteration. Since this function gets called with a frequency of
     * server.hz times per second, the following is the max amount of
     * microseconds we can spend in this function. */
    // 函数处理的微秒时间上限
    // ACTIVE_EXPIRE_CYCLE_SLOW_TIME_PERC 默认为 25 ，也即是 25 % 的 CPU 时间
    timelimit = 1000000*ACTIVE_EXPIRE_CYCLE_SLOW_TIME_PERC/server.hz/100;
    timelimit_exit = 0;
    if (timelimit <= 0) timelimit = 1;

    // 如果是运行在快速模式之下
    // 那么最多只能运行 FAST_DURATION 微秒 
    // 默认值为 1000 （微秒）
    if (type == ACTIVE_EXPIRE_CYCLE_FAST)
        timelimit = ACTIVE_EXPIRE_CYCLE_FAST_DURATION; /* in microseconds. */

    // 遍历数据库
    for (j = 0; j < dbs_per_call; j++) {
        int expired;
        // 指向要处理的数据库
        redisDb *db = server.db+(current_db % server.dbnum);

        /* Increment the DB now so we are sure if we run out of time
         * in the current DB we'll restart from the next. This allows to
         * distribute the time evenly across DBs. */
        // 为 DB 计数器加一，如果进入 do 循环之后因为超时而跳出
        // 那么下次会直接从下个 DB 开始处理
        current_db++;

        /* Continue to expire if at the end of the cycle more than 25%
         * of the keys were expired. */
        do {
            unsigned long num, slots;
            long long now, ttl_sum;
            int ttl_samples;

            /* If there is nothing to expire try next DB ASAP. */
            // 获取数据库中带过期时间的键的数量
            // 如果该数量为 0 ，直接跳过这个数据库
            if ((num = dictSize(db->expires)) == 0) {
                db->avg_ttl = 0;
                break;
            }
            // 获取数据库中键值对的数量
            slots = dictSlots(db->expires);
            // 当前时间
            now = mstime();

            /* When there are less than 1% filled slots getting random
             * keys is expensive, so stop here waiting for better times...
             * The dictionary will be resized asap. */
            // 这个数据库的使用率低于 1% ，扫描起来太费力了（大部分都会 MISS）
            // 跳过，等待字典收缩程序运行
            if (num && slots > DICT_HT_INITIAL_SIZE &&
                (num*100/slots < 1)) break;

            /* The main collection cycle. Sample random keys among keys
             * with an expire set, checking for expired ones. 
             *
             * 样本计数器
             */
            // 已处理过期键计数器
            expired = 0;
            // 键的总 TTL 计数器
            ttl_sum = 0;
            // 总共处理的键计数器
            ttl_samples = 0;

            // 每次最多只能检查 LOOKUPS_PER_LOOP 个键
            if (num > ACTIVE_EXPIRE_CYCLE_LOOKUPS_PER_LOOP)
                num = ACTIVE_EXPIRE_CYCLE_LOOKUPS_PER_LOOP;

            // 开始遍历数据库
            while (num--) {
                dictEntry *de;
                long long ttl;

                // 从 expires 中随机取出一个带过期时间的键
                if ((de = dictGetRandomKey(db->expires)) == NULL) break;
                // 计算 TTL
                ttl = dictGetSignedIntegerVal(de)-now;
                // 如果键已经过期，那么删除它，并将 expired 计数器增一
                if (activeExpireCycleTryExpire(db,de,now)) expired++;
                if (ttl < 0) ttl = 0;
                // 累积键的 TTL
                ttl_sum += ttl;
                // 累积处理键的个数
                ttl_samples++;
            }

            /* Update the average TTL stats for this database. */
            // 为这个数据库更新平均 TTL 统计数据
            if (ttl_samples) {
                // 计算当前平均值
                long long avg_ttl = ttl_sum/ttl_samples;
                
                // 如果这是第一次设置数据库平均 TTL ，那么进行初始化
                if (db->avg_ttl == 0) db->avg_ttl = avg_ttl;
                /* Smooth the value averaging with the previous one. */
                // 取数据库的上次平均 TTL 和今次平均 TTL 的平均值
                db->avg_ttl = (db->avg_ttl+avg_ttl)/2;
            }

            /* We can't block forever here even if there are many keys to
             * expire. So after a given amount of milliseconds return to the
             * caller waiting for the other active expire cycle. */
            // 我们不能用太长时间处理过期键，
            // 所以这个函数执行一定时间之后就要返回

            // 更新遍历次数
            iteration++;

            // 每遍历 16 次执行一次
            if ((iteration & 0xf) == 0 && /* check once every 16 iterations. */
                (ustime()-start) > timelimit)
            {
                // 如果遍历次数正好是 16 的倍数
                // 并且遍历的时间超过了 timelimit
                // 那么断开 timelimit_exit
                timelimit_exit = 1;
            }

            // 已经超时了，返回
            if (timelimit_exit) return;

            /* We don't repeat the cycle if there are less than 25% of keys
             * found expired in the current DB. */
            // 如果已删除的过期键占当前总数据库带过期时间的键数量的 25 %
            // 那么不再遍历
        } while (expired > ACTIVE_EXPIRE_CYCLE_LOOKUPS_PER_LOOP/4);
    }
}
```
每当Redis的服务器周期性操作redis.c/serverCron函数执行时，actieExpireCycle函数就会被调用，它在规定的时间内，分多次遍历服务器中的各个数据库，从数据库的expires字典中随机检查一部分键的过期时间，并删除其中的过期键。

##**2、RDB持久化**
> 关键字：RDB文件解析，自动间隔性保存

##**3、AOF持久化**
> 关键字：AOF持久化：文件写入与同步，AOF文件重写，数据一致性

##**4、事件**
> 关键字：I/O并发模式，文件事件处理器，时间事件处理器

##**5、客户端**
> 关键字：伪客户端

##**6、服务端器**
> 关键字：命令执行过程，服务器启动过程
