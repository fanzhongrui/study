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

###过期删除函数

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

###定期删除函数
过期键的定期删除策略由redis.c/activeExpireCycle函数实现。
每当Redis的服务器周期性操作redis.c/serverCron函数执行时，actieExpireCycle函数就会被调用，它在规定的时间内，分多次遍历服务器中的各个数据库，从数据库的expires字典中随机检查一部分键的过期时间，并删除其中的过期键。

activeExpireCycle函数的工作模式总结如下：

- 函数每次运行时，都从一定数量的数据库中取出一定数量的随机键进行检查，并删除其中的过期键
- 全局变量current_db会记录当前activeExpireCycle函数检查的进度，并在下一次activeExpireCycle函数调用时，接着上一次的进度进行处理。
- 随着activeExpireCycle函数的不断执行，服务器中所有数据库都会被检查一遍，这时函数将current_db变量重置为0，然后再次开始新一轮的检查工作

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

###RDB对过期键的处理

在执行save命令或者BGSAVE命令创建一个新的RDB文件时，程序会对数据库中的键进行检查，已过期的键不会被保存到新创建的RDB文件中。

所以，数据库中包含过期键不会对生存新的RDB文件造成影响。

在启动Redis服务器时，若服务器开启了RDB功能，那么服务器将对RDB文件进行载入：

- 如果服务器以主服务器模式运行，那么载入RDB文件时，程序会对文件中保存的键进行检查，未过期的键会被载入到数据库中，而过期键会被忽略，所以过期键对载入RDB的主服务器不会造成影响
- 若服务器以从服务器模式运行，那么载入RDB时，文件中保存的所有键，不论是否过期，都会被载入到数据库中。但是，因为主从服务器在进行数据同步的时候，从服务器的数据库就会被清空，所以，过期键对载入RDB文件的从服务器也不会造成影响

###AOF对过期键的处理

在执行AOF重写时，程序会对数据库中的键进行检查，已过期的键不会被保存到重写后的AOF文件中。

###复制对过期键的处理

当服务器运行在复制模式下时，从服务器的过期键删除动作由主服务器控制：

- 主服务器在删除一个过期键之后，会显式地向所有从服务器发送一个del命令，告知从服务器删除这个过期键
- 从服务器在执行客户端发送的读命令时，即使碰到过期键也不会将过期键删除，而是继续像处理未过期键一样处理过期键
- 从服务器只有在接到主服务器发来的del命令之后，才会删除过期键。

###数据库通知

数据库通知可以让客户端通过订阅给定的频道或模式，来获知数据库中键的变化，以及数据库中命令的执行情况。

数据库通知分为两类：

- 键空间通知（key-space notification）：某个键执行了什么命令
- 键事件通知（key-event notification）：某个命令被什么键执行了

服务器配置的notify-keyspace-events选项决定了服务器所发送通知的类型：

- 所有类型的键空间和键事件通知：AKE
- 所有类型的键空间通知：AK
- 所有类型的键事件通知：AE
- 字符串类型的键空间通知：K$
- 列表类型的键事件通知：El

发送数据库通知的功能是由notify.c/notifyKeyspaceEvent函数实现的：
void notifyKeyspaceEvent(int type, char *event, robj *key, int dbid);

- type参数是当前想要发送的通知的类型，程序会根据这个值判断通知是否就是服务器配置notify-keyspace-events选项所选定的通知类型，从而决定是否发送通知。

 - event:事件的名称

- keys：产生事件的键

- dbid：产生事件的数据库号码

函数会根据type参数以及这三个参数构建事件通知的内容，以及接收通知的频道名

每当一个Redis命令需要发送数据库通知的时候，该命令的实现函数就会调用notifyKeyspaceEvent函数，并向函数传递该命令所引发的事件的相关信息。

notifyKeyspaceEvent函数执行以下操作：

1. server.notify_keyspace_events属性就是服务器配置notify_keyspace_events选项所设置的值，如果给定的通知类型type不是服务器允许发送的通知类型，那么函数会直接返回，不做任何动作。
2. 如果给定的通知是服务器允许发送的通知，那么下一步函数会检测服务器是否允许发送键空间通知，若允许，程序就会构建并发送事件通知。
3. 最后，函数检测服务器是否允许发送键事件通知，若允许，程序就会构建并发送事件通知

```
/* The API provided to the rest of the Redis core is a simple function:
 *
 * notifyKeyspaceEvent(char *event, robj *key, int dbid);
 *
 * 'event' is a C string representing the event name.
 *
 * event 参数是一个字符串表示的事件名
 *
 * 'key' is a Redis object representing the key name.
 *
 * key 参数是一个 Redis 对象表示的键名
 *
 * 'dbid' is the database ID where the key lives.  
 *
 * dbid 参数为键所在的数据库
 */
void notifyKeyspaceEvent(int type, char *event, robj *key, int dbid) {
    sds chan;
    robj *chanobj, *eventobj;
    int len = -1;
    char buf[24];

    /* If notifications for this class of events are off, return ASAP. */
    // 如果服务器配置为不发送 type 类型的通知，那么直接返回
    if (!(server.notify_keyspace_events & type)) return;

    // 事件的名字
    eventobj = createStringObject(event,strlen(event));

    /* __keyspace@<db>__:<key> <event> notifications. */
    // 发送键空间通知
    if (server.notify_keyspace_events & REDIS_NOTIFY_KEYSPACE) {

        // 构建频道对象
        chan = sdsnewlen("__keyspace@",11);
        len = ll2string(buf,sizeof(buf),dbid);
        chan = sdscatlen(chan, buf, len);
        chan = sdscatlen(chan, "__:", 3);
        chan = sdscatsds(chan, key->ptr);

        chanobj = createObject(REDIS_STRING, chan);

        // 通过 publish 命令发送通知
        pubsubPublishMessage(chanobj, eventobj);

        // 释放频道对象
        decrRefCount(chanobj);
    }

    /* __keyevente@<db>__:<event> <key> notifications. */
    // 发送键事件通知
    if (server.notify_keyspace_events & REDIS_NOTIFY_KEYEVENT) {

        // 构建频道对象
        chan = sdsnewlen("__keyevent@",11);
        // 如果在前面发送键空间通知的时候计算了 len ，那么它就不会是 -1
        // 这可以避免计算两次 buf 的长度
        if (len == -1) len = ll2string(buf,sizeof(buf),dbid);
        chan = sdscatlen(chan, buf, len);
        chan = sdscatlen(chan, "__:", 3);
        chan = sdscatsds(chan, eventobj->ptr);

        chanobj = createObject(REDIS_STRING, chan);

        // 通过 publish 命令发送通知
        pubsubPublishMessage(chanobj, key);

        // 释放频道对象
        decrRefCount(chanobj);
    }

    // 释放事件对象
    decrRefCount(eventobj);
}
```

##**2、RDB持久化**
> 关键字：RDB文件解析，自动间隔性保存

Redis提供RDB持久化功能，可以将Redis在内存中的数据库状态保存到磁盘里，避免数据意外丢失。

RDB持久化可以手动执行，也可以根据服务器配置选项定期执行，该功能可以将某个时间点上的数据库状态保存到一个RDB文件中。

###RDB文件生成
RDB持久化功能所生成的RDB文件是一个经过压缩的二进制文件，通过该文件可以还原生成RDB文件时的数据库状态。

save命令生成RDB文件的方式是阻塞Redis服务器进程，直到RDB文件创建完毕，在服务器阻塞期间，服务器不能处理任何命令请求

BGSAVE命令的方式是派生出一个子进程，然后由子进程负责创建RDB文件，服务器进程（父进程）继续处理命令请求

创建RDB文件的实际工作都是由rdb.c/rdbSave函数完成，save命令和bgsave命令会以不同的方式调用这个函数。
```
/* Save the DB on disk. Return REDIS_ERR on error, REDIS_OK on success 
 *
 * 将数据库保存到磁盘上。
 *
 * 保存成功返回 REDIS_OK ，出错/失败返回 REDIS_ERR 。
 */
int rdbSave(char *filename) {
    dictIterator *di = NULL;
    dictEntry *de;
    char tmpfile[256];
    char magic[10];
    int j;
    long long now = mstime();
    FILE *fp;
    rio rdb;
    uint64_t cksum;

    // 创建临时文件
    snprintf(tmpfile,256,"temp-%d.rdb", (int) getpid());
    fp = fopen(tmpfile,"w");
    if (!fp) {
        redisLog(REDIS_WARNING, "Failed opening .rdb for saving: %s",
            strerror(errno));
        return REDIS_ERR;
    }

    // 初始化 I/O
    rioInitWithFile(&rdb,fp);

    // 设置校验和函数
    if (server.rdb_checksum)
        rdb.update_cksum = rioGenericUpdateChecksum;

    // 写入 RDB 版本号
    snprintf(magic,sizeof(magic),"REDIS%04d",REDIS_RDB_VERSION);
    if (rdbWriteRaw(&rdb,magic,9) == -1) goto werr;

    // 遍历所有数据库
    for (j = 0; j < server.dbnum; j++) {

        // 指向数据库
        redisDb *db = server.db+j;

        // 指向数据库键空间
        dict *d = db->dict;

        // 跳过空数据库
        if (dictSize(d) == 0) continue;

        // 创建键空间迭代器
        di = dictGetSafeIterator(d);
        if (!di) {
            fclose(fp);
            return REDIS_ERR;
        }

        /* Write the SELECT DB opcode 
         *
         * 写入 DB 选择器
         */
        if (rdbSaveType(&rdb,REDIS_RDB_OPCODE_SELECTDB) == -1) goto werr;
        if (rdbSaveLen(&rdb,j) == -1) goto werr;

        /* Iterate this DB writing every entry 
         *
         * 遍历数据库，并写入每个键值对的数据
         */
        while((de = dictNext(di)) != NULL) {
            sds keystr = dictGetKey(de);
            robj key, *o = dictGetVal(de);
            long long expire;
            
            // 根据 keystr ，在栈中创建一个 key 对象
            initStaticStringObject(key,keystr);

            // 获取键的过期时间
            expire = getExpire(db,&key);

            // 保存键值对数据
            if (rdbSaveKeyValuePair(&rdb,&key,o,expire,now) == -1) goto werr;
        }
        dictReleaseIterator(di);
    }
    di = NULL; /* So that we don't release it again on error. */

    /* EOF opcode 
     *
     * 写入 EOF 代码
     */
    if (rdbSaveType(&rdb,REDIS_RDB_OPCODE_EOF) == -1) goto werr;

    /* CRC64 checksum. It will be zero if checksum computation is disabled, the
     * loading code skips the check in this case. 
     *
     * CRC64 校验和。
     *
     * 如果校验和功能已关闭，那么 rdb.cksum 将为 0 ，
     * 在这种情况下， RDB 载入时会跳过校验和检查。
     */
    cksum = rdb.cksum;
    memrev64ifbe(&cksum);
    rioWrite(&rdb,&cksum,8);

    /* Make sure data will not remain on the OS's output buffers */
    // 冲洗缓存，确保数据已写入磁盘
    if (fflush(fp) == EOF) goto werr;
    if (fsync(fileno(fp)) == -1) goto werr;
    if (fclose(fp) == EOF) goto werr;

    /* Use RENAME to make sure the DB file is changed atomically only
     * if the generate DB file is ok. 
     *
     * 使用 RENAME ，原子性地对临时文件进行改名，覆盖原来的 RDB 文件。
     */
    if (rename(tmpfile,filename) == -1) {
        redisLog(REDIS_WARNING,"Error moving temp DB file on the final destination: %s", strerror(errno));
        unlink(tmpfile);
        return REDIS_ERR;
    }

    // 写入完成，打印日志
    redisLog(REDIS_NOTICE,"DB saved on disk");

    // 清零数据库脏状态
    server.dirty = 0;

    // 记录最后一次完成 SAVE 的时间
    server.lastsave = time(NULL);

    // 记录最后一次执行 SAVE 的状态
    server.lastbgsave_status = REDIS_OK;

    return REDIS_OK;

werr:
    // 关闭文件
    fclose(fp);
    // 删除文件
    unlink(tmpfile);

    redisLog(REDIS_WARNING,"Write error saving DB on disk: %s", strerror(errno));

    if (di) dictReleaseIterator(di);

    return REDIS_ERR;
}
```
###RDB文件生成条件
RDB文件的载入工作是在服务器启动时自动执行的，所以redis没有专门用于载入RDB文件的命令，只要redis服务器在启动时检测到RDB文件存在，就会自动载入RDB文件

因为AOF文件的更新频率通常比RDB文件的更新频率高，所以：

- 若服务器开启了AOF持久化功能，那么服务器就优先使用AOF文件还原数据库状态
- 只有在AOF持久化功能处于关闭状态时，服务器才会使用RDB文件来还原数据库状态

BGSAVE命令执行期间，客户端发送的save和BGSAVE命令都会被服务器拒绝，防止产生竞态条件

从性能考虑，BGREWRITEAOF和BGSAVE命令不能同时执行：

- 若BGSAVE命令正在执行，那么客户端发送的BGREWRITEAOF命令会被延迟到BGSAVE命令执行完毕之后执行
- BGREWRITEAOF命令正在执行，那么客户端发送的BGSAVE命令会被服务器拒绝

服务器在载入RDB文件期间，会一直处于阻塞状态，直到载入工作完成为止

当Redis服务器启动时，用户可以通过指定配置文件或传入启动参数的方式设置save选项，如果用户没有主动设置save选项，那么服务器会为save选项设置默认条件：

- save 900 1
- save 300 10
- save 60 10000
服务器程序会根据save选项设置服务器状态redisSever结构的saveparams属性：

```
struct redisServer{
    // 自从上次 SAVE 执行以来，数据库被修改的次数
    long long dirty;                /* Changes to DB from the last save */

    // BGSAVE 执行前的数据库被修改次数
    long long dirty_before_bgsave;  /* Used to restore dirty on failed BGSAVE */
    // 负责执行 BGSAVE 的子进程的 ID
    // 没在执行 BGSAVE 时，设为 -1
    pid_t rdb_child_pid;            /* PID of RDB saving child */
    // 记录了保存条件的数组
    struct saveparam *saveparams;   /* Save points array for RDB */
    int saveparamslen;              /* Number of saving points */
    char *rdb_filename;             /* Name of RDB file */
    int rdb_compression;            /* Use compression in RDB? */
    int rdb_checksum;               /* Use RDB checksum? */

    // 最后一次完成 SAVE 的时间
    time_t lastsave;                /* Unix time of last successful save */

    // 最后一次尝试执行 BGSAVE 的时间
    time_t lastbgsave_try;          /* Unix time of last attempted bgsave */

    // 最近一次 BGSAVE 执行耗费的时间
    time_t rdb_save_time_last;      /* Time used by last RDB save run. */

    // 数据库最近一次开始执行 BGSAVE 的时间
    time_t rdb_save_time_start;     /* Current RDB save start time. */

    // 最后一次执行 SAVE 的状态
    int lastbgsave_status;          /* REDIS_OK or REDIS_ERR */
    int stop_writes_on_bgsave_err;  /* Don't allow writes if can't BGSAVE */

}
```
```
// 服务器的保存条件（BGSAVE 自动执行的条件）
struct saveparam {
    // 多少秒之内
    time_t seconds;
    // 发生多少次修改，修改数
    int changes;

};
```
- saveparams属性是一个数组，数组中的每个元素都是一个saveparam结构，每个saveparam结构都保存了一个save选项设置的保存条件
- dirty计数器：记录距离上一次成功执行save命令或BGSAVE命令之后，服务器对数据库状态（服务器中所有数据库）进行了多少次修改（包括写入、删除、更新等操作）
- lastsave属性是一个UNIX时间戳，记录了服务器上一次成功执行save命令BGSAVE命令的时间

Redis服务器周期性操作函数serverCron默认每隔100毫秒执行一次，该函数用于对正在运行的服务器进行维护，它其中一项工作就是检查save选项所设置的保存条件是否已经满足，若满足，就执行BGSAVE命令
```
/* This is our timer interrupt, called server.hz times per second.
 *
 * 这是 Redis 的时间中断器，每秒调用 server.hz 次。
 *
 * Here is where we do a number of things that need to be done asynchronously.
 * For instance:
 *
 * 以下是需要异步执行的操作：
 *
 * - Active expired keys collection (it is also performed in a lazy way on
 *   lookup).
 *   主动清除过期键。
 *
 * - Software watchdog.
 *   更新软件 watchdog 的信息。
 *
 * - Update some statistic.
 *   更新统计信息。
 *
 * - Incremental rehashing of the DBs hash tables.
 *   对数据库进行渐增式 Rehash
 *
 * - Triggering BGSAVE / AOF rewrite, and handling of terminated children.
 *   触发 BGSAVE 或者 AOF 重写，并处理之后由 BGSAVE 和 AOF 重写引发的子进程停止。
 *
 * - Clients timeout of different kinds.
 *   处理客户端超时。
 *
 * - Replication reconnection.
 *   复制重连
 *
 * - Many more...
 *   等等。。。
 *
 * Everything directly called here will be called server.hz times per second,
 * so in order to throttle execution of things we want to do less frequently
 * a macro is used: run_with_period(milliseconds) { .... }
 *
 * 因为 serverCron 函数中的所有代码都会每秒调用 server.hz 次，
 * 为了对部分代码的调用次数进行限制，
 * 使用了一个宏 run_with_period(milliseconds) { ... } ，
 * 这个宏可以将被包含代码的执行次数降低为每 milliseconds 执行一次。
 */

int serverCron(struct aeEventLoop *eventLoop, long long id, void *clientData) {
    
    //....
  /* If there is not a background saving/rewrite in progress check if
         * we have to save/rewrite now */
        // 既然没有 BGSAVE 或者 BGREWRITEAOF 在执行，那么检查是否需要执行它们

        // 遍历所有保存条件，看是否需要执行 BGSAVE 命令
         for (j = 0; j < server.saveparamslen; j++) {
            struct saveparam *sp = server.saveparams+j;

            /* Save if we reached the given amount of changes,
             * the given amount of seconds, and if the latest bgsave was
             * successful or if, in case of an error, at least
             * REDIS_BGSAVE_RETRY_DELAY seconds already elapsed. */
            // 检查是否有某个保存条件已经满足了
            if (server.dirty >= sp->changes &&
                server.unixtime-server.lastsave > sp->seconds &&
                (server.unixtime-server.lastbgsave_try >
                 REDIS_BGSAVE_RETRY_DELAY ||
                 server.lastbgsave_status == REDIS_OK))
            {
                redisLog(REDIS_NOTICE,"%d changes in %d seconds. Saving...",
                    sp->changes, (int)sp->seconds);
                // 执行 BGSAVE
                rdbSaveBackground(server.rdb_filename);
                break;
            }
         }
    // ....

}
```

###RDB文件格式
由上文rdbSave()函数可以看出，一个完整RDB文件所包含的各个部分有

> REDIS | db_version | database | EOF | check_sum

- RDB文件的最开头是redis部分，长度为5字节，保存“REDIS”五个字符。通过这五个字符，程序可以在载入文件时，快速检查所载入的文件是否是RDB文件
- db_version长度为4字节，值是一个字符串表示的整数，记录RDB文件的版本号
- database部分包含零个或任意多个数据库，以及各个数据库中的键值对数据，若所有数据库均为空则这个部分也为空，长度为0
- EOF常量，长度1字节，标识RDB文件正文内容的结束。
- check_sum是一个8字节长的无符号整数，保存着一个校验和，是程序对REDIS、db_version、database、EOF四个部分的内容进行计算得出的。

服务器载入RDB文件时，会将载入数据所计算出的校验和与check_sum所记录的校验和进行对比，以此来检查RDB文件是否有出错或损坏的情况

一个RDB文件的database部分可以保存任意多个非空数据库。每个非空数据库在RDB文件中都可以保存为SELECTDB、db_number、key_value_pairs三个部分：
> SELECTDB | db_number | key_value_pairs

- SELECTDB常量的长度为1字节，当读入程序遇到这个值的时候，它知道接下来要读入的将是一个数据库号码
- db_number保存着一个数据库号码，根据号码的大小不同，这个部分的长度可以是1字节、2字节或5字节。当程序度日db_number部分之后，服务器会调用select命令，根据读入的数据库号码进行数据库切换，使得之后读入的键值对可以载入到正确的数据库中。
- key_value_pairs部分保存了数据库中的所有键值对数据，如果键值对带有过期时间，那么过期时间会和键值对保存在一起。根据键值对的数量、类型、内容以及是否有过期时间等条件的不同，key_value_pairs部分的长度也会有所不同。

不带过期时间的键值对在RDB文件中由type、key、value三部分组成。

- type记录了value的类型，长度为1字节，值为常量,如REDIS_RDB_TYPE_STRING、REDIS_RDB_TYPE_LIST_ZIPLIST、REDIS_RDB_TYPE_HASH_ZIPLIST等
    + type常量代表了一种对象类型或者底层编码，当服务器读入RDB文件中的键值对数据时，程序会根据type的值来决定如何读入和解释value的数据

- key总是一个字符串对象
- 根据type类型的不同，以及保存内容长度的不同，保存value的结构和长度也会有所不同

带有过期时间的键值对结构为：
> EXPIRETIME | ms | TYPE | key | value

- EXPIRETIME_MS常量的长度为1字节，表示接下来读入的将是一个以毫秒为单位的过期时间
- ms是一个8字节长的带符号整数，记录着一个以毫秒为单位的UNIX时间戳，即键值对的过期时间

###**value的编码**

RDB文件中的每个value部分都保存了一个值对象，每个值对象的类型都由与之对应的TYPE记录，根据类型的不同，value部分的结构、长度也会有所不同

- 1、字符串对象，type为：REDIS_RDB_TYPE_STRING

> 字符串对象的编码若为REDIS_ENCODING_INT：对象中保存的是长度不超过32位的整数
>              ENCODING | Integer
>  ENCODING的值可以是REDIS_RDB_ENC_INT8、REDIS_RDB_ENC_INT16或者REDIS_RDB_ENC_INT32三个常量中的一个，分别代表RDB文件使用8位、16位或32位保存整数值Integer
>  
> 字符串对象的编码若为REDIS_ENCODING_RAW：说明对象保存的是一个字符串值
> 若字符串长度小于等于20字节，那么字符串会被原样保存：   len | string
> 若字符串长度大于20字节，那么这个字符串会被压缩之后再保存: 
> REDIS_RDB_ENC_LZF | compressed_len | origin_len | compressed_string
> REDIS_RDB_ENC_LZF常量表示字符串被LZF算法压缩过

- 2、列表对象，type为：REDIS_RDB_TYPE_LIST

> value保存的是一个REDIS_ENCODING_LINKEDLIST编码的列表对象，结构：
> list_length | item1 | item2 | 。。。| itemn

- 3、集合对象，type为：REDIS_RDB_TYPE_SET

> value保存的是一个REDIS_ENCODING_HT编码的集合对象，结构：
> set_size | elem1 | elem2 | ... | elemn

- 4、哈希表对象，type为：REDIS_RDB_TYPE_HASH

> value保存的是一个REDIS_ENCODING_HT编码的集合对象，结构：
> hash_size | key_value_pair 1 | ... | key_value_pair N

- 5、有序集合对象，type为：REDIS_RDB_TYPE_ZSET

> value保存的是REDIS_ENCODING_SKIPLIST编码的有序集合对象，结构：
> sorted_set_size | member1|score1 | member2|score2 | ... | memberN|scoreN

- 6、INTSET编码的集合，type为：REDIS_RDB_TYPE_STRING

> value保存的是一个整数集合对象，RDB保存这种对象的方法是，先将整数集合对象转换为字符串对象，然后将这个字符串对象保存到RDB文件中
> 如果程序在读入RDB文件的过程中，遇到由整数集合对象转换成的字符串对象，那么程序会根据TYPE值的指示，先读入字符串对象，再将这个字符串对象转换成原来的整数集合对象

- 7、ZIPLIST编码的列表、哈希表或者有序集合，type为：REDIS_RDB_TYPE_ZIPLIST、REDIS_RDB_TYPE_HASH_ZIPLIST、REDIS_RDB_TYPE_ZSET_ZIPLIST

> value保存的是一个压缩列表对象，RDB文件保存这种对象的方法是：
> 将压缩列表转换成一个字符串对象
> 将字符串对象保存到RDB文件

###**分析RDB文件**

可以使用-od命令分析redis服务器产生的RDB文件，该命令可以用给定的格式转存（dump）并打印输入文件，比如，给定-c参数可以以ASCII编码的方式打印输入文件，给定-x参数可以以十六进制的方式打印输入文件

##**3、AOF持久化**
> 关键字：AOF持久化：文件写入与同步，AOF文件重写，数据一致性

与RDB持久化通过保存数据库中的键值对来记录数据库状态不同，AOF持久化是通过保存redis服务器所执行的写命令来记录数据库状态的

被写入AOF文件的所有命令都是以redis的命令请求协议格式保存的，因为redis的命令请求协议是纯文本格式，所以可以直接打开一个AOF文件，观察里面的内容

AOF文件初始会自动添加一个用于指定数据库的select命令。

服务器在启动时，可以通过载入和执行AOF文件中保存的命令来还原服务器关闭之前的数据库状态

###AOF持久化的实现

AOF持久化功能的实现分为命令追加（append）、文件写入、文件同步（sync）三个步骤。

```
struct redisServer{
    // ...
     /* AOF persistence */

    // AOF 状态（开启/关闭/可写）
    int aof_state;                  /* REDIS_AOF_(ON|OFF|WAIT_REWRITE) */

    // 所使用的 fsync 策略（每个写入/每秒/从不）
    int aof_fsync;                  /* Kind of fsync() policy */
    char *aof_filename;             /* Name of the AOF file */
    int aof_no_fsync_on_rewrite;    /* Don't fsync if a rewrite is in prog. */
    int aof_rewrite_perc;           /* Rewrite AOF if % growth is > M and... */
    off_t aof_rewrite_min_size;     /* the AOF file is at least N bytes. */

    // 最后一次执行 BGREWRITEAOF 时， AOF 文件的大小
    off_t aof_rewrite_base_size;    /* AOF size on latest startup or rewrite. */

    // AOF 文件的当前字节大小
    off_t aof_current_size;         /* AOF current size. */
    //记录服务器是否延迟了bgrewriteaof即AOF重写命令，如果值为1 ，表示有AOF重写命令被延迟了
    int aof_rewrite_scheduled;      /* Rewrite once BGSAVE terminates. */

    // 负责进行 AOF 重写的子进程 ID，如果没有执行，属性值为-1
    pid_t aof_child_pid;            /* PID if rewriting process */

    // AOF 重写缓存链表，链接着多个缓存块
    list *aof_rewrite_buf_blocks;   /* Hold changes during an AOF rewrite. */

    // AOF 缓冲区
    sds aof_buf;      /* AOF buffer, written before entering the event loop */

    // AOF 文件的描述符
    int aof_fd;       /* File descriptor of currently selected AOF file */

    // AOF 的当前目标数据库
    int aof_selected_db; /* Currently selected DB in AOF */

    // 推迟 write 操作的时间
    time_t aof_flush_postponed_start; /* UNIX time of postponed AOF flush */

    // 最后一直执行 fsync 的时间
    time_t aof_last_fsync;            /* UNIX time of last fsync() */
    time_t aof_rewrite_time_last;   /* Time used by last AOF rewrite run. */

    // AOF 重写的开始时间
    time_t aof_rewrite_time_start;  /* Current AOF rewrite start time. */

    // 最后一次执行 BGREWRITEAOF 的结果
    int aof_lastbgrewrite_status;   /* REDIS_OK or REDIS_ERR */

    // 记录 AOF 的 write 操作被推迟了多少次
    unsigned long aof_delayed_fsync;  /* delayed AOF fsync() counter */

    // 指示是否需要每写入一定量的数据，就主动执行一次 fsync()
    int aof_rewrite_incremental_fsync;/* fsync incrementally while rewriting? */
    int aof_last_write_status;      /* REDIS_OK or REDIS_ERR */
    int aof_last_write_errno;       /* Valid if aof_last_write_status is ERR */
    // ...
};
```
当AOF持久化功能处于打开状态（aof_state为REDIS_AOF_ON）时，服务器在执行完一个写命令之后，会以协议格式将被执行的写命令追加到服务器的aof_buf缓冲区的末尾

Redis的服务器进程就是一个事件循环（loop），这个循环中的文件事件负责接收客户端的命令请求，以及向客户端发送命令回复，而时间事件则负责执行像serverCron函数这样需要定时运行的函数

因为服务器在处理文件事件时可能会执行写命令，使得一些内容被追加到aof_buf缓冲区里面，所以在服务器每次结束一个事件循环之前，它都会调用flushAppendOnlyFile函数，考虑是否需要将aof_buf缓冲区中的内容写入和保存到AOF文件里面：
```
/* Write the append only file buffer on disk.
 *
 * 将 AOF 缓存写入到文件中。
 *
 * Since we are required to write the AOF before replying to the client,
 * and the only way the client socket can get a write is entering when the
 * the event loop, we accumulate all the AOF writes in a memory
 * buffer and write it on disk using this function just before entering
 * the event loop again.
 *
 * 因为程序需要在回复客户端之前对 AOF 执行写操作。
 * 而客户端能执行写操作的唯一机会就是在事件 loop 中，
 * 因此，程序将所有 AOF 写累积到缓存中，
 * 并在重新进入事件 loop 之前，将缓存写入到文件中。
 *
 * About the 'force' argument:
 *
 * 关于 force 参数：
 *
 * When the fsync policy is set to 'everysec' we may delay the flush if there
 * is still an fsync() going on in the background thread, since for instance
 * on Linux write(2) will be blocked by the background fsync anyway.
 *
 * 当 fsync 策略为每秒钟保存一次时，如果后台线程仍然有 fsync 在执行，
 * 那么我们可能会延迟执行冲洗（flush）操作，
 * 因为 Linux 上的 write(2) 会被后台的 fsync 阻塞。
 *
 * When this happens we remember that there is some aof buffer to be
 * flushed ASAP, and will try to do that in the serverCron() function.
 *
 * 当这种情况发生时，说明需要尽快冲洗 aof 缓存，
 * 程序会尝试在 serverCron() 函数中对缓存进行冲洗。
 *
 * However if force is set to 1 we'll write regardless of the background
 * fsync. 
 *
 * 不过，如果 force 为 1 的话，那么不管后台是否正在 fsync ，
 * 程序都直接进行写入。
 */
#define AOF_WRITE_LOG_ERROR_RATE 30 /* Seconds between errors logging. */
void flushAppendOnlyFile(int force) {
    ssize_t nwritten;
    int sync_in_progress = 0;

    // 缓冲区中没有任何内容，直接返回
    if (sdslen(server.aof_buf) == 0) return;

    // 策略为每秒 FSYNC 
    if (server.aof_fsync == AOF_FSYNC_EVERYSEC)
        // 是否有 SYNC 正在后台进行？
        sync_in_progress = bioPendingJobsOfType(REDIS_BIO_AOF_FSYNC) != 0;

    // 每秒 fsync ，并且强制写入为假
    if (server.aof_fsync == AOF_FSYNC_EVERYSEC && !force) {

        /* With this append fsync policy we do background fsyncing.
         *
         * 当 fsync 策略为每秒钟一次时， fsync 在后台执行。
         *
         * If the fsync is still in progress we can try to delay
         * the write for a couple of seconds. 
         *
         * 如果后台仍在执行 FSYNC ，那么我们可以延迟写操作一两秒
         * （如果强制执行 write 的话，服务器主线程将阻塞在 write 上面）
         */
        if (sync_in_progress) {

            // 有 fsync 正在后台进行 。。。

            if (server.aof_flush_postponed_start == 0) {
                /* No previous write postponinig, remember that we are
                 * postponing the flush and return. 
                 *
                 * 前面没有推迟过 write 操作，这里将推迟写操作的时间记录下来
                 * 然后就返回，不执行 write 或者 fsync
                 */
                server.aof_flush_postponed_start = server.unixtime;
                return;

            } else if (server.unixtime - server.aof_flush_postponed_start < 2) {
                /* We were already waiting for fsync to finish, but for less
                 * than two seconds this is still ok. Postpone again. 
                 *
                 * 如果之前已经因为 fsync 而推迟了 write 操作
                 * 但是推迟的时间不超过 2 秒，那么直接返回
                 * 不执行 write 或者 fsync
                 */
                return;

            }

            /* Otherwise fall trough, and go write since we can't wait
             * over two seconds. 
             *
             * 如果后台还有 fsync 在执行，并且 write 已经推迟 >= 2 秒
             * 那么执行写操作（write 将被阻塞）
             */
            server.aof_delayed_fsync++;
            redisLog(REDIS_NOTICE,"Asynchronous AOF fsync is taking too long (disk is busy?). Writing the AOF buffer without waiting for fsync to complete, this may slow down Redis.");
        }
    }

    /* If you are following this code path, then we are going to write so
     * set reset the postponed flush sentinel to zero. 
     *
     * 执行到这里，程序会对 AOF 文件进行写入。
     *
     * 清零延迟 write 的时间记录
     */
    server.aof_flush_postponed_start = 0;

    /* We want to perform a single write. This should be guaranteed atomic
     * at least if the filesystem we are writing is a real physical one.
     *
     * 执行单个 write 操作，如果写入设备是物理的话，那么这个操作应该是原子的
     *
     * While this will save us against the server being killed I don't think
     * there is much to do about the whole server stopping for power problems
     * or alike 
     *
     * 当然，如果出现像电源中断这样的不可抗现象，那么 AOF 文件也是可能会出现问题的
     * 这时就要用 redis-check-aof 程序来进行修复。
     */
    nwritten = write(server.aof_fd,server.aof_buf,sdslen(server.aof_buf));
    if (nwritten != (signed)sdslen(server.aof_buf)) {

        static time_t last_write_error_log = 0;
        int can_log = 0;

        /* Limit logging rate to 1 line per AOF_WRITE_LOG_ERROR_RATE seconds. */
        // 将日志的记录频率限制在每行 AOF_WRITE_LOG_ERROR_RATE 秒
        if ((server.unixtime - last_write_error_log) > AOF_WRITE_LOG_ERROR_RATE) {
            can_log = 1;
            last_write_error_log = server.unixtime;
        }

        /* Lof the AOF write error and record the error code. */
        // 如果写入出错，那么尝试将该情况写入到日志里面
        if (nwritten == -1) {
            if (can_log) {
                redisLog(REDIS_WARNING,"Error writing to the AOF file: %s",
                    strerror(errno));
                server.aof_last_write_errno = errno;
            }
        } else {
            if (can_log) {
                redisLog(REDIS_WARNING,"Short write while writing to "
                                       "the AOF file: (nwritten=%lld, "
                                       "expected=%lld)",
                                       (long long)nwritten,
                                       (long long)sdslen(server.aof_buf));
            }

            // 尝试移除新追加的不完整内容
            if (ftruncate(server.aof_fd, server.aof_current_size) == -1) {
                if (can_log) {
                    redisLog(REDIS_WARNING, "Could not remove short write "
                             "from the append-only file.  Redis may refuse "
                             "to load the AOF the next time it starts.  "
                             "ftruncate: %s", strerror(errno));
                }
            } else {
                /* If the ftrunacate() succeeded we can set nwritten to
                 * -1 since there is no longer partial data into the AOF. */
                nwritten = -1;
            }
            server.aof_last_write_errno = ENOSPC;
        }

        /* Handle the AOF write error. */
        // 处理写入 AOF 文件时出现的错误
        if (server.aof_fsync == AOF_FSYNC_ALWAYS) {
            /* We can't recover when the fsync policy is ALWAYS since the
             * reply for the client is already in the output buffers, and we
             * have the contract with the user that on acknowledged write data
             * is synched on disk. */
            redisLog(REDIS_WARNING,"Can't recover from AOF write error when the AOF fsync policy is 'always'. Exiting...");
            exit(1);
        } else {
            /* Recover from failed write leaving data into the buffer. However
             * set an error to stop accepting writes as long as the error
             * condition is not cleared. */
            server.aof_last_write_status = REDIS_ERR;

            /* Trim the sds buffer if there was a partial write, and there
             * was no way to undo it with ftruncate(2). */
            if (nwritten > 0) {
                server.aof_current_size += nwritten;
                sdsrange(server.aof_buf,nwritten,-1);
            }
            return; /* We'll try again on the next call... */
        }
    } else {
        /* Successful write(2). If AOF was in error state, restore the
         * OK state and log the event. */
        // 写入成功，更新最后写入状态
        if (server.aof_last_write_status == REDIS_ERR) {
            redisLog(REDIS_WARNING,
                "AOF write error looks solved, Redis can write again.");
            server.aof_last_write_status = REDIS_OK;
        }
    }

    // 更新写入后的 AOF 文件大小
    server.aof_current_size += nwritten;

    /* Re-use AOF buffer when it is small enough. The maximum comes from the
     * arena size of 4k minus some overhead (but is otherwise arbitrary). 
     *
     * 如果 AOF 缓存的大小足够小的话，那么重用这个缓存，
     * 否则的话，释放 AOF 缓存。
     */
    if ((sdslen(server.aof_buf)+sdsavail(server.aof_buf)) < 4000) {
        // 清空缓存中的内容，等待重用
        sdsclear(server.aof_buf);
    } else {
        // 释放缓存
        sdsfree(server.aof_buf);
        server.aof_buf = sdsempty();
    }

    /* Don't fsync if no-appendfsync-on-rewrite is set to yes and there are
     * children doing I/O in the background. 
     *
     * 如果 no-appendfsync-on-rewrite 选项为开启状态，
     * 并且有 BGSAVE 或者 BGREWRITEAOF 正在进行的话，
     * 那么不执行 fsync 
     */
    if (server.aof_no_fsync_on_rewrite &&
        (server.aof_child_pid != -1 || server.rdb_child_pid != -1))
            return;

    /* Perform the fsync if needed. */

    // 总是执行 fsnyc
    if (server.aof_fsync == AOF_FSYNC_ALWAYS) {
        /* aof_fsync is defined as fdatasync() for Linux in order to avoid
         * flushing metadata. */
        aof_fsync(server.aof_fd); /* Let's try to get this data on the disk */

        // 更新最后一次执行 fsnyc 的时间
        server.aof_last_fsync = server.unixtime;

    // 策略为每秒 fsnyc ，并且距离上次 fsync 已经超过 1 秒
    } else if ((server.aof_fsync == AOF_FSYNC_EVERYSEC &&
                server.unixtime > server.aof_last_fsync)) {
        // 放到后台执行
        if (!sync_in_progress) aof_background_fsync(server.aof_fd);
        // 更新最后一次执行 fsync 的时间
        server.aof_last_fsync = server.unixtime;
    }

    // 其实上面无论执行 if 部分还是 else 部分都要更新 fsync 的时间
    // 可以将代码挪到下面来
    // server.aof_last_fsync = server.unixtime;
}
```
flushAppendOnlyFile函数的行为由服务器配置的appendfsync选项的值来决定“

- always：服务器在每个事件循环都要将aof_buf缓冲区中的所有内容写入并同步到AOF文件，效率最慢但最安全

- everysec：服务器在每个事件循环都要将aof_buf缓冲区中的所有内容写入到AOF文件，如果上次同步AOF文件的时间距离现在超过1s，那么再次对AOF文件进行同步，并且这个同步操作是由一个线程专门负责执行的

- no：服务器在每个事件循环都要将aof_buf缓冲区中的所有内容写入到AOF文件，但不对AOF文件进行同步，何时同步由操作系统决定，速度最快，安全性最低
- 默认为everysec

用户调用write函数，将一些数据写入到文件的时候，操作系统通常会将写入数据暂时保存在一个内存缓冲区中，等到缓冲区的空间被填满、或者超过了指定的时限之后，才真正地将缓冲区中的数据写入到磁盘。  
虽然提高了效率，但为写入数据带来了安全问题，如果计算机发生停机，那么保存在内存缓冲区里面的写入数据将会丢失。

redis提供了fsync和fdatasync两个同步函数，它们可以强制让操作系统立即将缓冲区中的数据写入到硬盘中，从而确保写入数据的安全性。

###AOF文件的载入与数据还原

redis读取AOF文件并还原数据库状态的详细步骤如下：

1. 创建一个不带网络连接的伪客户端（fake client）：因为redis的命令只能在客户端上下文中执行，而载入AOF文件时所使用的命令直接来源于AOF文件而不是网络连接，所以服务器使用了一个没有网络连接的伪客户端来执行AOF文件保存的写命令，伪客户端执行命令的效果和带网络连接的客户端执行命令的效果完全一样。

2. 从AOF文件中分析并读取一条写命令
3. 使用伪客户端执行被读出的写命令
4. 一直执行步骤2和步骤3，直到AOF文件中的所有写命令都被处理完毕为止

###AOF重写
为了解决AOF文件体积膨胀问题，redis提供了AOF文件重写（rewrite）功能。通过该功能，Redis服务器可以创建一个新的AOF文件来替代现有的AOF文件，新旧两个AOF文件所保存的数据库状态相同，但新AOF文件不会包含任何浪费空间的冗余命令，所以新AOF文件的体积通常会比旧AOF文件体积要小得多。

AOF文件重写并不需要对现有的AOF文件进行任何读取、分析或写入操作，这个功能是通过读取服务器当前的数据库状态来实现的。

首先从数据库中读取键现在的值，然后用一条命令去记录键值对，代替之前记录这个键值对的多条命令，这就是AOF重写功能的实现原理。

rewriteAppendOnlyFile函数的实现：
```
/* Write a sequence of commands able to fully rebuild the dataset into
 * "filename". Used both by REWRITEAOF and BGREWRITEAOF.
 *
 * 将一个足以还原当前数据集的命令序列写入到 filename 指定的文件中。
 *
 * 这个函数被 REWRITEAOF 和 BGREWRITEAOF 两个命令调用。
 * （REWRITEAOF 似乎已经是一个废弃的命令）
 *
 * In order to minimize the number of commands needed in the rewritten
 * log Redis uses variadic commands when possible, such as RPUSH, SADD
 * and ZADD. However at max REDIS_AOF_REWRITE_ITEMS_PER_CMD items per time
 * are inserted using a single command. 
 *
 * 为了最小化重建数据集所需执行的命令数量，
 * Redis 会尽可能地使用接受可变参数数量的命令，比如 RPUSH 、SADD 和 ZADD 等。
 *
 * 不过单个命令每次处理的元素数量不能超过 REDIS_AOF_REWRITE_ITEMS_PER_CMD 。
 */
int rewriteAppendOnlyFile(char *filename) {
    dictIterator *di = NULL;
    dictEntry *de;
    rio aof;
    FILE *fp;
    char tmpfile[256];
    int j;
    long long now = mstime();

    /* Note that we have to use a different temp name here compared to the
     * one used by rewriteAppendOnlyFileBackground() function. 
     *
     * 创建临时文件
     *
     * 注意这里创建的文件名和 rewriteAppendOnlyFileBackground() 创建的文件名稍有不同
     */
    snprintf(tmpfile,256,"temp-rewriteaof-%d.aof", (int) getpid());
    fp = fopen(tmpfile,"w");
    if (!fp) {
        redisLog(REDIS_WARNING, "Opening the temp file for AOF rewrite in rewriteAppendOnlyFile(): %s", strerror(errno));
        return REDIS_ERR;
    }

    // 初始化文件 io
    rioInitWithFile(&aof,fp);

    // 设置每写入 REDIS_AOF_AUTOSYNC_BYTES 字节
    // 就执行一次 FSYNC 
    // 防止缓存中积累太多命令内容，造成 I/O 阻塞时间过长
    if (server.aof_rewrite_incremental_fsync)
        rioSetAutoSync(&aof,REDIS_AOF_AUTOSYNC_BYTES);

    // 遍历所有数据库
    for (j = 0; j < server.dbnum; j++) {

        char selectcmd[] = "*2\r\n$6\r\nSELECT\r\n";

        redisDb *db = server.db+j;

        // 指向键空间
        dict *d = db->dict;
        if (dictSize(d) == 0) continue;

        // 创建键空间迭代器
        di = dictGetSafeIterator(d);
        if (!di) {
            fclose(fp);
            return REDIS_ERR;
        }

        /* SELECT the new DB 
         *
         * 首先写入 SELECT 命令，确保之后的数据会被插入到正确的数据库上
         */
        if (rioWrite(&aof,selectcmd,sizeof(selectcmd)-1) == 0) goto werr;
        if (rioWriteBulkLongLong(&aof,j) == 0) goto werr;

        /* Iterate this DB writing every entry 
         *
         * 遍历数据库所有键，并通过命令将它们的当前状态（值）记录到新 AOF 文件中
         */
        while((de = dictNext(di)) != NULL) {
            sds keystr;
            robj key, *o;
            long long expiretime;

            // 取出键
            keystr = dictGetKey(de);

            // 取出值
            o = dictGetVal(de);
            initStaticStringObject(key,keystr);

            // 取出过期时间
            expiretime = getExpire(db,&key);

            /* If this key is already expired skip it 
             *
             * 如果键已经过期，那么跳过它，不保存
             */
            if (expiretime != -1 && expiretime < now) continue;

            /* Save the key and associated value 
             *
             * 根据值的类型，选择适当的命令来保存值
             */
            if (o->type == REDIS_STRING) {
                /* Emit a SET command */
                char cmd[]="*3\r\n$3\r\nSET\r\n";
                if (rioWrite(&aof,cmd,sizeof(cmd)-1) == 0) goto werr;
                /* Key and value */
                if (rioWriteBulkObject(&aof,&key) == 0) goto werr;
                if (rioWriteBulkObject(&aof,o) == 0) goto werr;
            } else if (o->type == REDIS_LIST) {
                if (rewriteListObject(&aof,&key,o) == 0) goto werr;
            } else if (o->type == REDIS_SET) {
                if (rewriteSetObject(&aof,&key,o) == 0) goto werr;
            } else if (o->type == REDIS_ZSET) {
                if (rewriteSortedSetObject(&aof,&key,o) == 0) goto werr;
            } else if (o->type == REDIS_HASH) {
                if (rewriteHashObject(&aof,&key,o) == 0) goto werr;
            } else {
                redisPanic("Unknown object type");
            }

            /* Save the expire time 
             *
             * 保存键的过期时间
             */
            if (expiretime != -1) {
                char cmd[]="*3\r\n$9\r\nPEXPIREAT\r\n";

                // 写入 PEXPIREAT expiretime 命令
                if (rioWrite(&aof,cmd,sizeof(cmd)-1) == 0) goto werr;
                if (rioWriteBulkObject(&aof,&key) == 0) goto werr;
                if (rioWriteBulkLongLong(&aof,expiretime) == 0) goto werr;
            }
        }

        // 释放迭代器
        dictReleaseIterator(di);
    }

    /* Make sure data will not remain on the OS's output buffers */
    // 冲洗并关闭新 AOF 文件
    if (fflush(fp) == EOF) goto werr;
    if (aof_fsync(fileno(fp)) == -1) goto werr;
    if (fclose(fp) == EOF) goto werr;

    /* Use RENAME to make sure the DB file is changed atomically only
     * if the generate DB file is ok. 
     *
     * 原子地改名，用重写后的新 AOF 文件覆盖旧 AOF 文件
     */
    if (rename(tmpfile,filename) == -1) {
        redisLog(REDIS_WARNING,"Error moving temp append only file on the final destination: %s", strerror(errno));
        unlink(tmpfile);
        return REDIS_ERR;
    }

    redisLog(REDIS_NOTICE,"SYNC append only file rewrite performed");

    return REDIS_OK;

werr:
    fclose(fp);
    unlink(tmpfile);
    redisLog(REDIS_WARNING,"Write error writing append only file on disk: %s", strerror(errno));
    if (di) dictReleaseIterator(di);
    return REDIS_ERR;
}
```

###AOF后台重写
将AOF重写程序放到子进程里执行，可以达到两个目的：

- 子进程进行AOF重写期间，服务器进程（父进程）可以继续处理命令请求
- 子进程带有服务器进程的数据副本，使用子进程而不是线程，可以在避免使用锁的情况下，保证数据的安全性

不过，子进程在进行AOF重写期间，服务器进程还需要继续处理命令请求，而新的命令可能会对所有的数据库状态进行修改，从而使得服务器当前的数据库状态和重写后的AOF文件所保存的数据库状态不一致。

为了解决这种数据不一致问题，redis服务器设置了一个AOF重写缓冲区，这个缓冲区在服务器创建子进程之后开始使用，当redis服务器执行完一个写命令之后，它会同时将这个写命令发送给AOF缓冲区和AOF重写缓冲区。

这也就是说，在子进程执行AOF重写期间，服务器进程需要执行以下三个工作：

1. 执行客户端发来的命令
2. 将执行后的写命令追加到AOF缓冲区
3. 将执行后的写命令追加到AOF重写缓冲区

这样一来，可以保证:

- AOF缓冲区带内容会定期被写入和同步到AOF文件，对现有AOF文件的处理工作会如常进行
- 从创建子进程开始，服务器执行的所有写命令都被记录到AOF重写缓冲区里

当子进程完成AOF重写工作之后，它会向父进程发送一个信号，父进程在接到该信号之后，会调用一个 *信号处理函数* ，并执行以下工作：

- 将AOF重写缓冲区中的所有内容写入到新AOF文件中，这时新AOF文件所保存的数据库状态和服务器当前的数据库状态一致
- 对新的AOF文件进行改名，原子地（atomic）覆盖现有的AOF文件，完成新旧两个AOF文件的替换

*信号处理函数* 执行完毕之后，父进程就可以继续像往常一样接受命令请求了。

在整个AOF后台重写过程中，只有 *信号处理函数* 执行时会对服务器进程（父进程）造成阻塞，其他时候，AOF后台重写都不会阻塞父进程，这将AOF重写对服务器性能造成的影响降到了最低。

rewriteAppendOnlyFileBackground函数的实现：
```
/* This is how rewriting of the append only file in background works:
 * 
 * 以下是后台重写 AOF 文件（BGREWRITEAOF）的工作步骤：
 *
 * 1) The user calls BGREWRITEAOF
 *    用户调用 BGREWRITEAOF
 *
 * 2) Redis calls this function, that forks():
 *    Redis 调用这个函数，它执行 fork() ：
 *
 *    2a) the child rewrite the append only file in a temp file.
 *        子进程在临时文件中对 AOF 文件进行重写
 *
 *    2b) the parent accumulates differences in server.aof_rewrite_buf.
 *        父进程将新输入的写命令追加到 server.aof_rewrite_buf 中
 *
 * 3) When the child finished '2a' exists.
 *    当步骤 2a 执行完之后，子进程结束
 *
 * 4) The parent will trap the exit code, if it's OK, will append the
 *    data accumulated into server.aof_rewrite_buf into the temp file, and
 *    finally will rename(2) the temp file in the actual file name.
 *    The the new file is reopened as the new append only file. Profit!
 *
 *    父进程会捕捉子进程的退出信号，
 *    如果子进程的退出状态是 OK 的话，
 *    那么父进程将新输入命令的缓存追加到临时文件，
 *    然后使用 rename(2) 对临时文件改名，用它代替旧的 AOF 文件，
 *    至此，后台 AOF 重写完成。
 */
int rewriteAppendOnlyFileBackground(void) {
    pid_t childpid;
    long long start;

    // 已经有进程在进行 AOF 重写了
    if (server.aof_child_pid != -1) return REDIS_ERR;

    // 记录 fork 开始前的时间，计算 fork 耗时用
    start = ustime();

    if ((childpid = fork()) == 0) {
        char tmpfile[256];

        /* Child */

        // 关闭网络连接 fd
        closeListeningSockets(0);

        // 为进程设置名字，方便记认
        redisSetProcTitle("redis-aof-rewrite");

        // 创建临时文件，并进行 AOF 重写
        snprintf(tmpfile,256,"temp-rewriteaof-bg-%d.aof", (int) getpid());
        if (rewriteAppendOnlyFile(tmpfile) == REDIS_OK) {
            size_t private_dirty = zmalloc_get_private_dirty();

            if (private_dirty) {
                redisLog(REDIS_NOTICE,
                    "AOF rewrite: %zu MB of memory used by copy-on-write",
                    private_dirty/(1024*1024));
            }
            // 发送重写成功信号
            exitFromChild(0);
        } else {
            // 发送重写失败信号
            exitFromChild(1);
        }
    } else {
        /* Parent */
        // 记录执行 fork 所消耗的时间
        server.stat_fork_time = ustime()-start;

        if (childpid == -1) {
            redisLog(REDIS_WARNING,
                "Can't rewrite append only file in background: fork: %s",
                strerror(errno));
            return REDIS_ERR;
        }

        redisLog(REDIS_NOTICE,
            "Background append only file rewriting started by pid %d",childpid);

        // 记录 AOF 重写的信息
        server.aof_rewrite_scheduled = 0;
        server.aof_rewrite_time_start = time(NULL);
        server.aof_child_pid = childpid;

        // 关闭字典自动 rehash
        updateDictResizePolicy();

        /* We set appendseldb to -1 in order to force the next call to the
         * feedAppendOnlyFile() to issue a SELECT command, so the differences
         * accumulated by the parent into server.aof_rewrite_buf will start
         * with a SELECT statement and it will be safe to merge. 
         *
         * 将 aof_selected_db 设为 -1 ，
         * 强制让 feedAppendOnlyFile() 下次执行时引发一个 SELECT 命令，
         * 从而确保之后新添加的命令会设置到正确的数据库中
         */
        server.aof_selected_db = -1;
        replicationScriptCacheFlush();
        return REDIS_OK;
    }
    return REDIS_OK; /* unreached */
}
```

##**4、事件**
> 关键字：I/O并发模式，文件事件处理器，时间事件处理器

##**5、客户端**
> 关键字：伪客户端

##**6、服务端器**
> 关键字：命令执行过程，服务器启动过程
