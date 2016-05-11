package java.util;

import java.io.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.BiFunction;

/**
 * Hashtable实现了哈希表，把关键字映射到值，不允许空值null
 * 作为关键字的对象必须实现hashCode和equals方法，以便从hashtable中存取对象
 * 一个Hashtable对象有两个参数影响其性能：初始容量和装载因子。容量是哈希表中槽bucket的数目，初始容量只是创建时候的容量
 * 哈希表对于哈希冲突是开放式的，一个槽中可能会存储多个Entry，遍历的时候只能顺序遍历。
 * 装载因子用来衡量哈希表达到多满的程度的时候需要重新哈希，具体什么时候是否需要调用rehash方法还要看具体实现
 * 通常默认的装载因子0.75提供了一个很好的时空取舍
 * 初始容量控制了空间浪费与相当耗费时间的rehash操作的必要性之间的取舍。较大的初始容量可以保证不必rehash，但初始容量过大又容易浪费空间。
 * 如果有很多Entry需要放进Hashtable，那就创建一个初始容量比较大的哈希表，比自动增长rehash的插入更加高效
 * hashtable的使用例子：
 *   Hashtable<String, Integer> numbers
 *     = new Hashtable<String, Integer>();
 *   numbers.put("one", 1);
 *   numbers.put("two", 2);
 *   numbers.put("three", 3);}
 * 取数据的操作：
 *   Integer n = numbers.get("two");
 *   if (n != null) {
 *     System.out.println("two = " + n);
 *   }}
 * 通过iterator方法返回的迭代器都是fail-fast的：如果迭代器创建之后hashtable发生除了通过迭代器自己的remove函数之外的结构改变，都会抛出ConcurrentModificationException异常
 * Hashtable的keys和elements方法返回的枚举集合enumerator不是fail-fast的
 */
/**
 * Hashtable继承于Dictionary，实现了Map、Cloneable、java.io.Serializable接口
 * Hashtable的函数都是同步的，这意味着它是线程安全的。它的key、value都不可以为null。此外，Hashtable中的映射不是有序的。
 * 涉及到结构改变的函数操作都使用synchronized修饰
 */
public class Hashtable<K,V>
    extends Dictionary<K,V>
    implements Map<K,V>, Cloneable, java.io.Serializable {

    /**
     * 哈希表中存放数据的地方.
     */
    private transient Entry<?,?>[] table;

    /**
     * 哈希表中所有Entry的数目
     */
    private transient int count;

    /**
     * 重新哈希的阈值（threshold = (int)capacity * loadFactor）
     */
    private int threshold;

    /**
     * hashtable的装载因子
     */
    private float loadFactor;

    /**
     * Hashtable的结构修改次数。结构修改指的是改变Entry数目或是修改内部结构（如rehash）
     * 这个字段用来使Hashtable的集合视图fail-fast的。
     */
    private transient int modCount = 0;

    /** use serialVersionUID from JDK 1.0.2 for interoperability */
    private static final long serialVersionUID = 1421746759512286392L;

    /**
     * 构造函数1：构造一个指定容量和装载因子的空哈希表
     * @param      initialCapacity   the initial capacity of the hashtable.
     * @param      loadFactor        the load factor of the hashtable.
     * @exception  IllegalArgumentException  if the initial capacity is less
     *             than zero, or if the load factor is nonpositive.
     */
    public Hashtable(int initialCapacity, float loadFactor) {
        if (initialCapacity < 0)
            throw new IllegalArgumentException("Illegal Capacity: "+
                                               initialCapacity);
        if (loadFactor <= 0 || Float.isNaN(loadFactor))
            throw new IllegalArgumentException("Illegal Load: "+loadFactor);

        if (initialCapacity==0)
            initialCapacity = 1;
        this.loadFactor = loadFactor;
        table = new Entry<?,?>[initialCapacity];
        threshold = (int)Math.min(initialCapacity * loadFactor, MAX_ARRAY_SIZE + 1);
    }

    /**
     * 构造函数2：构造一个指定容量的空哈希表，默认装载因子0.75
     * @param     initialCapacity   the initial capacity of the hashtable.
     * @exception IllegalArgumentException if the initial capacity is less
     *              than zero.
     */
    public Hashtable(int initialCapacity) {
        this(initialCapacity, 0.75f);
    }

    /**
     * 构造函数3：构造一个空哈希表，默认容量11，默认装载因子0.75
     */
    public Hashtable() {
        this(11, 0.75f);
    }

    /**
     * 构造函数4：构造一个包含子Map的构造函数，容量为足够容纳指定Map中元素的2的次幂，默认装载因子0.75
     * @param t the map whose mappings are to be placed in this map.
     * @throws NullPointerException if the specified map is null.
     */
    public Hashtable(Map<? extends K, ? extends V> t) {
        this(Math.max(2*t.size(), 11), 0.75f);
        putAll(t);
    }

    /**
     * 返回Hashtable中键值对的数目，方法由synchronized修饰，支持同步调用
     * @return  the number of keys in this hashtable.
     */
    public synchronized int size() {
        return count;
    }

    /**
     * 测试Hashtable是否为空，方法由synchronized修饰，支持同步调用
     * @return  <code>true</code> if this hashtable maps no keys to values;
     *          <code>false</code> otherwise.
     */
    public synchronized boolean isEmpty() {
        return count == 0;
    }

    /**
     * 返回Hashtable中所有关键字的一个枚举集合，方法由synchronized修饰，支持同步调用
     * @return  an enumeration of the keys in this hashtable.
     * @see     Enumeration
     * @see     #elements()
     * @see     #keySet()
     * @see     Map
     */
    public synchronized Enumeration<K> keys() {
        return this.<K>getEnumeration(KEYS);
    }

    /**
     * 返回Hashtable中所有值对象的枚举集合，使用返回对象的getEnumeration方法顺序获取元素
     * @return  an enumeration of the values in this hashtable.
     */
    public synchronized Enumeration<V> elements() {
        return this.<V>getEnumeration(VALUES);
    }

    /**
     * 测试Hashtable中是否有关键字映射到指定值上。contains(value)比containsKey(key)方法耗时多一些。
     * 这个方法与Map接口containsValue方法功能相同
     * @param      value   a value to search for
     * @return     <code>true</code> if and only if some key maps to the
     *             <code>value</code> argument in this hashtable as
     *             determined by the <tt>equals</tt> method;
     *             <code>false</code> otherwise.
     * @exception  NullPointerException  if the value is <code>null</code>
     */
    public synchronized boolean contains(Object value) {
    		//Hashtable中“键值对”的value不能使null，否则抛出异常NullPointerException
        if (value == null) {
            throw new NullPointerException();
        }
        	//从后向前遍历table数组中的元素（Entry）
        	//对于每个Entry（单向链表），逐个遍历，判断结点的值是否等于value
        Entry<?,?> tab[] = table;
        for (int i = tab.length ; i-- > 0 ;) {
            for (Entry<?,?> e = tab[i] ; e != null ; e = e.next) {
                if (e.value.equals(value)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 与contains功能一样，本质就是调用了contains函数
     * @param value value whose presence in this hashtable is to be tested
     * @return <tt>true</tt> if this map maps one or more keys to the
     *         specified value
     * @throws NullPointerException  if the value is <code>null</code>
     * @since 1.2
     */
    public boolean containsValue(Object value) {
        return contains(value);
    }

    /**
     * 测试指定Key是否存在
     * @param   key   possible key
     * @return  <code>true</code> if and only if the specified object
     *          is a key in this hashtable, as determined by the
     *          <tt>equals</tt> method; <code>false</code> otherwise.
     * @throws  NullPointerException  if the key is <code>null</code>
     */
    public synchronized boolean containsKey(Object key) {
        Entry<?,?> tab[] = table;
        int hash = key.hashCode();
        	//关键字Key映射的哈希槽下标
        int index = (hash & 0x7FFFFFFF) % tab.length;
        	//遍历链表找到与指定Key相等（equals）的元素
        for (Entry<?,?> e = tab[index] ; e != null ; e = e.next) {
            if ((e.hash == hash) && e.key.equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 返回指定关键字Key的value值，不存在则返回null
     * @param key the key whose associated value is to be returned
     * @return the value to which the specified key is mapped, or
     *         {@code null} if this map contains no mapping for the key
     * @throws NullPointerException if the specified key is null
     * @see     #put(Object, Object)
     */
    @SuppressWarnings("unchecked")
    public synchronized V get(Object key) {
        Entry<?,?> tab[] = table;
        int hash = key.hashCode();
        	//计算指定关键字映射的哈希槽
        int index = (hash & 0x7FFFFFFF) % tab.length;
        	//遍历单向链表
        for (Entry<?,?> e = tab[index] ; e != null ; e = e.next) {
            if ((e.hash == hash) && e.key.equals(key)) {
                return (V)e.value;
            }
        }
        return null;
    }

    /**
     * 分配数组最大容量，一些虚拟机会保存数组的头字，试图分配更大的数组会导致OOM（OutOfMemoryError）：请求的数组容量超出VM限制
     */
    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

    /**
     * 容量增长时需要内部重新组织Hashtable，以更有效率的访问
     * 当Hashtable中关键字数目超出容量与装载因子之积时，自动调用该方法
     */
    @SuppressWarnings("unchecked")
    protected void rehash() {
        int oldCapacity = table.length;	//旧容量
        Entry<?,?>[] oldMap = table;	//旧Entry数组

        // 溢出检测（超出MAX_ARRAY_SIZE）
        int newCapacity = (oldCapacity << 1) + 1;
        if (newCapacity - MAX_ARRAY_SIZE > 0) {
            if (oldCapacity == MAX_ARRAY_SIZE)
                // Keep running with MAX_ARRAY_SIZE buckets
                return;
            newCapacity = MAX_ARRAY_SIZE;
        }
        	//申请新Entry数组
        Entry<?,?>[] newMap = new Entry<?,?>[newCapacity];
        	//修改modCount
        modCount++;
        	//修改新阈值（新阈值也不能超过MAX_ARRAY_SIZE）
        threshold = (int)Math.min(newCapacity * loadFactor, MAX_ARRAY_SIZE + 1);
        table = newMap;
        	//从后向前遍历旧表每一个槽中的链表的每一个Entry元素，将其重新哈希到新表中
        for (int i = oldCapacity ; i-- > 0 ;) {
            for (Entry<K,V> old = (Entry<K,V>)oldMap[i] ; old != null ; ) {
                Entry<K,V> e = old;
                old = old.next;

                int index = (e.hash & 0x7FFFFFFF) % newCapacity;
                	//将e插入Index槽中当前链表的开头
                e.next = (Entry<K,V>)newMap[index];
                newMap[index] = e;
            }
        }
    }
    	//添加新的Entry元素
    private void addEntry(int hash, K key, V value, int index) {
        modCount++;

        Entry<?,?> tab[] = table;
        	//超过阈值，需要重新哈希
        if (count >= threshold) {
            // Rehash the table if the threshold is exceeded
            rehash();

            tab = table;
            hash = key.hashCode();
            index = (hash & 0x7FFFFFFF) % tab.length;
        }
        // 创建新的Entry，并插入Index槽中链表的头部
        @SuppressWarnings("unchecked")
        Entry<K,V> e = (Entry<K,V>) tab[index];
        tab[index] = new Entry<>(hash, key, value, e);
        count++;
    }

    /**
     * 将指定的Key映射到指定的value。Key和value都不能为空null
     * @param      key     the hashtable key
     * @param      value   the value
     * @return     the previous value of the specified key in this hashtable,
     *             or <code>null</code> if it did not have one
     * @exception  NullPointerException  if the key or value is
     *               <code>null</code>
     */
    public synchronized V put(K key, V value) {
        // 确保value不为空null
        if (value == null) {
            throw new NullPointerException();
        }
        // 确保Key在Hashtable中不存在，若存在，更新value，并返回旧值
        Entry<?,?> tab[] = table;
        int hash = key.hashCode();
        int index = (hash & 0x7FFFFFFF) % tab.length;
        @SuppressWarnings("unchecked")
        Entry<K,V> entry = (Entry<K,V>)tab[index];
        for(; entry != null ; entry = entry.next) {
            if ((entry.hash == hash) && entry.key.equals(key)) {
                V old = entry.value;
                entry.value = value;
                return old;
            }
        }
        	//不存在，添加元素
        addEntry(hash, key, value, index);
        return null;
    }

    /**
     * 删除关键字Key相关的Entry，如果不存在Key，就什么都不做（只是遍历一趟链表。。）
     * @param   key   the key that needs to be removed
     * @return  the value to which the key had been mapped in this hashtable,
     *          or <code>null</code> if the key did not have a mapping
     * @throws  NullPointerException  if the key is <code>null</code>
     */
    public synchronized V remove(Object key) {
        Entry<?,?> tab[] = table;
        int hash = key.hashCode();
        	//获取下标Index
        int index = (hash & 0x7FFFFFFF) % tab.length;
        @SuppressWarnings("unchecked")
        Entry<K,V> e = (Entry<K,V>)tab[index];
        for(Entry<K,V> prev = null ; e != null ; prev = e, e = e.next) {
            if ((e.hash == hash) && e.key.equals(key)) {
                modCount++;
                	//删除e
                if (prev != null) {
                    prev.next = e.next;
                } else {
                    tab[index] = e.next;
                }
                count--;
                V oldValue = e.value;
                e.value = null;
                	//返回删除元素的value值
                return oldValue;
            }
        }
        return null;
    }

    /**
     * 将指定Map中的所有映射都拷贝到Hashtable中，已经存在的Key对应的value值会被更新
     * @param t mappings to be stored in this map
     * @throws NullPointerException if the specified map is null
     */
    public synchronized void putAll(Map<? extends K, ? extends V> t) {
        for (Map.Entry<? extends K, ? extends V> e : t.entrySet())
            put(e.getKey(), e.getValue());
    }

    /**
     * 清空Hashtable，将Hashtable的table数组的值全部设为null
     */
    public synchronized void clear() {
        Entry<?,?> tab[] = table;
        modCount++;
        for (int index = tab.length; --index >= 0; )
            tab[index] = null;
        count = 0;
    }

    /**
     * 创建一个Hashtable的浅拷贝。Hashtable自身的结构都被拷贝了（拷贝数组，拷贝链表），但是其中的关键字和值不拷贝（依然引用的同一份Key和value）。
     * @return  a clone of the hashtable
     */
    public synchronized Object clone() {
        try {
            Hashtable<?,?> t = (Hashtable<?,?>)super.clone();
            t.table = new Entry<?,?>[table.length];
            for (int i = table.length ; i-- > 0 ; ) {
            		//依次调用数组里的链表的第一个元素的clone方法，后继元素都自动复制了，因为clone中会调用后继元素的clone
                t.table[i] = (table[i] != null)
                    ? (Entry<?,?>) table[i].clone() : null;
            }
            t.keySet = null;
            t.entrySet = null;
            t.values = null;
            t.modCount = 0;
            return t;
        } catch (CloneNotSupportedException e) {
            // this shouldn't happen, since we are Cloneable
            throw new InternalError(e);
        }
    }

    /**
     * 返回Hashtable对象的String表达方式，一系列以括号和逗号，空格分隔的Entry，如{key1=value1, key2=value2}
     * @return  a string representation of this hashtable
     */
    public synchronized String toString() {
        int max = size() - 1;
        	//Hashtable中元素为空
        if (max == -1)
            return "{}";
        	//使用StringBuilder,提高字符串拼接效率
        StringBuilder sb = new StringBuilder();
        	//获得Hashtable的Entry集合
        Iterator<Map.Entry<K,V>> it = entrySet().iterator();

        sb.append('{');
        for (int i = 0; ; i++) {
            Map.Entry<K,V> e = it.next();
            K key = e.getKey();
            V value = e.getValue();
            sb.append(key   == this ? "(this Map)" : key.toString());
            sb.append('=');
            sb.append(value == this ? "(this Map)" : value.toString());

            if (i == max)
                return sb.append('}').toString();
            sb.append(", ");
        }
    }

    	//获得指定类型（keys,values,entries）的枚举集合
    private <T> Enumeration<T> getEnumeration(int type) {
        if (count == 0) {
            return Collections.emptyEnumeration();
        } else {
        		//传false，新建枚举器
            return new Enumerator<>(type, false);
        }
    }
    	//获得指定类型（keys,values,entries）的迭代器
    private <T> Iterator<T> getIterator(int type) {
        if (count == 0) {
            return Collections.emptyIterator();
        } else {
        		//传true参数，新建迭代器类型实例
            return new Enumerator<>(type, true);
        }
    }

    // 视图

    /**
     * 以下每个字段初始化后会包含一个首次请求后的指定视图，视图是无状态的，所以不必创建多个
     */
    private transient volatile Set<K> keySet = null;
    private transient volatile Set<Map.Entry<K,V>> entrySet = null;
    private transient volatile Collection<V> values = null;

    /**
     * 返回Map的关键字视图Set，Map中的任何修改都会反映在Set中，反过来也是如此。当一个迭代器正在遍历时，如果Map的结构发生改变，迭代器行为未定义，
     * 如果是使用迭代器的remove函数改变Map结构，不会发生异常
     * set<K>支持Iterator.remove，Set.remove，removeAll，retainAll和clear函数
     * 不支持add和addAll函数
     */
    public Set<K> keySet() {
        if (keySet == null)
        		//返回线程安全的KeySet
            keySet = Collections.synchronizedSet(new KeySet(), this);
        return keySet;
    }
    //KeySet类
    private class KeySet extends AbstractSet<K> {
        public Iterator<K> iterator() {
        		//返回关键字迭代器
            return getIterator(KEYS);
        }
        public int size() {
            return count;
        }
        public boolean contains(Object o) {
            return containsKey(o);
        }
        public boolean remove(Object o) {
            return Hashtable.this.remove(o) != null;
        }
        public void clear() {
            Hashtable.this.clear();
        }
    }

    /**
     * 返回Map中映射的集合Set，Map中的改变会反映在Set中，反之亦是如此。
     * 迭代器遍历Set时，如果Map结构发生变化，迭代器行为未定义，除了通过迭代器自身的remove操作和setValue操作
     * 该Set支持通过Iterator.remove,Set.remove, removeAll, retainAll和clear操作删除元素
     * 不支持add和addAll操作
     */
    public Set<Map.Entry<K,V>> entrySet() {
        if (entrySet==null)
        		//返回线程安全的entrySet
            entrySet = Collections.synchronizedSet(new EntrySet(), this);
        return entrySet;
    }

    private class EntrySet extends AbstractSet<Map.Entry<K,V>> {
        public Iterator<Map.Entry<K,V>> iterator() {
        		//返回Entry的迭代器
            return getIterator(ENTRIES);
        }

        public boolean add(Map.Entry<K,V> o) {
            return super.add(o);
        }

        public boolean contains(Object o) {
        		//确定类型
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?,?> entry = (Map.Entry<?,?>)o;
            Object key = entry.getKey();
            Entry<?,?>[] tab = table;
            int hash = key.hashCode();
            int index = (hash & 0x7FFFFFFF) % tab.length;

            for (Entry<?,?> e = tab[index]; e != null; e = e.next)
            		//找到指定Entry
                if (e.hash==hash && e.equals(entry))
                    return true;
            return false;
        }

        public boolean remove(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?,?> entry = (Map.Entry<?,?>) o;
            Object key = entry.getKey();
            Entry<?,?>[] tab = table;
            int hash = key.hashCode();
            	//确定下标
            int index = (hash & 0x7FFFFFFF) % tab.length;

            @SuppressWarnings("unchecked")
            Entry<K,V> e = (Entry<K,V>)tab[index];
            for(Entry<K,V> prev = null; e != null; prev = e, e = e.next) {
                if (e.hash==hash && e.equals(entry)) {
                    modCount++;
                    	//删除找到的元素
                    if (prev != null)
                        prev.next = e.next;
                    else
                        tab[index] = e.next;

                    count--;
                    e.value = null;
                    return true;
                }
            }
            return false;
        }

        public int size() {
            return count;
        }

        public void clear() {
            Hashtable.this.clear();
        }
    }

    /**
     * 返回Map中所有值的集合视图，Map中的任何修改都会反映在集合中，反之亦是如此。如果集合遍历过程中，Map发生结构上的修改，迭代行为未定义
     * 除了通过迭代器自身的remove函数，集合支持元素删除，通过Iterator.remove，Collection.remove，removeAll，retainAll和clear等行为操作，
     * 但不支持add和addAll操作
     */
    public Collection<V> values() {
        if (values==null)
        		//返回线程安全的值集合
            values = Collections.synchronizedCollection(new ValueCollection(),
                                                        this);
        return values;
    }

    private class ValueCollection extends AbstractCollection<V> {
        public Iterator<V> iterator() {
        		//返回Values迭代器
            return getIterator(VALUES);
        }
        public int size() {
            return count;
        }
        public boolean contains(Object o) {
            return containsValue(o);
        }
        public void clear() {
            Hashtable.this.clear();
        }
    }

    // 比较和哈希函数
    /**
     * 比较指定对象和当前Map，判断是否相等
     * @param  o object to be compared for equality with this hashtable
     * @return true if the specified Object is equal to this Map
     */
    public synchronized boolean equals(Object o) {
    		//同一个元素，返回true
        if (o == this)
            return true;
        	//类型不同，直接否定
        if (!(o instanceof Map))
            return false;
        Map<?,?> t = (Map<?,?>) o;
        if (t.size() != size())
            return false;

        try {
        		//获取Entry的迭代器
            Iterator<Map.Entry<K,V>> i = entrySet().iterator();
            	//判断Map中的每一个元素Entry的键值对是不是都在t中存在 
            while (i.hasNext()) {
                Map.Entry<K,V> e = i.next();
                K key = e.getKey();
                V value = e.getValue();
                	//如果有不相等或是不存在的，立刻返回false
                if (value == null) {
                    if (!(t.get(key)==null && t.containsKey(key)))
                        return false;
                } else {
                    if (!value.equals(t.get(key)))
                        return false;
                }
            }
        } catch (ClassCastException unused)   {
            return false;
        } catch (NullPointerException unused) {
            return false;
        }
        return true;
    }

    /**
     * 返回Map的哈希值，Map中每一个Entry的hashcode相加
     * @see Map#hashCode()
     */
    public synchronized int hashCode() {
        /*
         * 这段代码检测了由于哈希表自引用引起的递归计算，并阻止了栈溢出。
         * 这段代码复用了装载因子loadFactor字段的功能，作为一个正在计算的标识位，为了节省空间。
         * 装载因子为负说明正在计算hashcode
         */
        int h = 0;
        if (count == 0 || loadFactor < 0)
            return h;  // 返回0

        loadFactor = -loadFactor;  // Mark hashCode computation in progress
        Entry<?,?>[] tab = table;
        for (Entry<?,?> entry : tab) {
            while (entry != null) {
            		//累加哈希值
                h += entry.hashCode();
                entry = entry.next;
            }
        }

        loadFactor = -loadFactor;  // Mark hashCode computation complete
        return h;
    }

    @Override
    public synchronized V getOrDefault(Object key, V defaultValue) {
        V result = get(key);
        return (null == result) ? defaultValue : result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public synchronized void forEach(BiConsumer<? super K, ? super V> action) {
        Objects.requireNonNull(action);     // explicit check required in case
                                            // table is empty.
        final int expectedModCount = modCount;

        Entry<?, ?>[] tab = table;
        for (Entry<?, ?> entry : tab) {
            while (entry != null) {
                action.accept((K)entry.key, (V)entry.value);
                entry = entry.next;

                if (expectedModCount != modCount) {
                    throw new ConcurrentModificationException();
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public synchronized void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        Objects.requireNonNull(function);     // explicit check required in case
                                              // table is empty.
        final int expectedModCount = modCount;

        Entry<K, V>[] tab = (Entry<K, V>[])table;
        for (Entry<K, V> entry : tab) {
            while (entry != null) {
                entry.value = Objects.requireNonNull(
                    function.apply(entry.key, entry.value));
                entry = entry.next;

                if (expectedModCount != modCount) {
                    throw new ConcurrentModificationException();
                }
            }
        }
    }
    //存在Key就更新，不存在就添加
    @Override
    public synchronized V putIfAbsent(K key, V value) {
        Objects.requireNonNull(value);	//检测value非空

        // Makes sure the key is not already in the hashtable.
        Entry<?,?> tab[] = table;
        int hash = key.hashCode();
        int index = (hash & 0x7FFFFFFF) % tab.length;
        @SuppressWarnings("unchecked")
        Entry<K,V> entry = (Entry<K,V>)tab[index];
        for (; entry != null; entry = entry.next) {
            if ((entry.hash == hash) && entry.key.equals(key)) {
                V old = entry.value;
                if (old == null) {
                    entry.value = value;
                }
                return old;
            }
        }

        addEntry(hash, key, value, index);
        return null;
    }
    //删除指定Key-value对
    @Override
    public synchronized boolean remove(Object key, Object value) {
        Objects.requireNonNull(value);

        Entry<?,?> tab[] = table;
        int hash = key.hashCode();
        int index = (hash & 0x7FFFFFFF) % tab.length;
        @SuppressWarnings("unchecked")
        Entry<K,V> e = (Entry<K,V>)tab[index];
        for (Entry<K,V> prev = null; e != null; prev = e, e = e.next) {
            if ((e.hash == hash) && e.key.equals(key) && e.value.equals(value)) {
                modCount++;
                if (prev != null) {
                    prev.next = e.next;
                } else {
                    tab[index] = e.next;
                }
                count--;
                e.value = null;
                return true;
            }
        }
        return false;
    }
    //替换旧值为新值，如果key对应的值不等于旧值（equals），就不替换
    @Override
    public synchronized boolean replace(K key, V oldValue, V newValue) {
        Objects.requireNonNull(oldValue);
        Objects.requireNonNull(newValue);
        Entry<?,?> tab[] = table;
        int hash = key.hashCode();
        int index = (hash & 0x7FFFFFFF) % tab.length;
        @SuppressWarnings("unchecked")
        Entry<K,V> e = (Entry<K,V>)tab[index];
        for (; e != null; e = e.next) {
            if ((e.hash == hash) && e.key.equals(key)) {
                if (e.value.equals(oldValue)) {
                    e.value = newValue;
                    return true;
                } else {
                    return false;
                }
            }
        }
        return false;
    }
    //替换值，如果不存在key，返回null
    @Override
    public synchronized V replace(K key, V value) {
        Objects.requireNonNull(value);
        Entry<?,?> tab[] = table;
        int hash = key.hashCode();
        int index = (hash & 0x7FFFFFFF) % tab.length;
        @SuppressWarnings("unchecked")
        Entry<K,V> e = (Entry<K,V>)tab[index];
        for (; e != null; e = e.next) {
            if ((e.hash == hash) && e.key.equals(key)) {
                V oldValue = e.value;
                e.value = value;
                return oldValue;
            }
        }
        return null;
    }
    //如果不存在Key，就添加键值对key-value，value通过mappingFunction计算得到 
    @Override
    public synchronized V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        Objects.requireNonNull(mappingFunction);

        Entry<?,?> tab[] = table;
        int hash = key.hashCode();
        int index = (hash & 0x7FFFFFFF) % tab.length;
        @SuppressWarnings("unchecked")
        Entry<K,V> e = (Entry<K,V>)tab[index];
        for (; e != null; e = e.next) {
            if (e.hash == hash && e.key.equals(key)) {
                // Hashtable not accept null value
                return e.value;
            }
        }

        V newValue = mappingFunction.apply(key);
        if (newValue != null) {
            addEntry(hash, key, newValue, index);
        }

        return newValue;
    }
    
    //如果存在就替换Key的value值，value通过mappingFunction计算得到，如果计算得到的value为null，就删除Key对应的Entry
    @Override
    public synchronized V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        Objects.requireNonNull(remappingFunction);

        Entry<?,?> tab[] = table;
        int hash = key.hashCode();
        int index = (hash & 0x7FFFFFFF) % tab.length;
        @SuppressWarnings("unchecked")
        Entry<K,V> e = (Entry<K,V>)tab[index];
        for (Entry<K,V> prev = null; e != null; prev = e, e = e.next) {
            if (e.hash == hash && e.key.equals(key)) {
                V newValue = remappingFunction.apply(key, e.value);
                	//Hashtable不允许键值为null，删除Key对应的Entry
                if (newValue == null) {
                    modCount++;
                    if (prev != null) {
                        prev.next = e.next;
                    } else {
                        tab[index] = e.next;
                    }
                    count--;
                } else {
                		//不为空，替换为新值
                    e.value = newValue;
                }
                return newValue;
            }
        }
        return null;
    }

    @Override
    public synchronized V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        Objects.requireNonNull(remappingFunction);

        Entry<?,?> tab[] = table;
        int hash = key.hashCode();
        int index = (hash & 0x7FFFFFFF) % tab.length;
        @SuppressWarnings("unchecked")
        Entry<K,V> e = (Entry<K,V>)tab[index];
        for (Entry<K,V> prev = null; e != null; prev = e, e = e.next) {
            if (e.hash == hash && Objects.equals(e.key, key)) {
                V newValue = remappingFunction.apply(key, e.value);
                if (newValue == null) {
                    modCount++;
                    if (prev != null) {
                        prev.next = e.next;
                    } else {
                        tab[index] = e.next;
                    }
                    count--;
                } else {
                    e.value = newValue;
                }
                return newValue;
            }
        }

        V newValue = remappingFunction.apply(key, null);
        if (newValue != null) {
            addEntry(hash, key, newValue, index);
        }

        return newValue;
    }

    @Override
    public synchronized V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        Objects.requireNonNull(remappingFunction);

        Entry<?,?> tab[] = table;
        int hash = key.hashCode();
        int index = (hash & 0x7FFFFFFF) % tab.length;
        @SuppressWarnings("unchecked")
        Entry<K,V> e = (Entry<K,V>)tab[index];
        for (Entry<K,V> prev = null; e != null; prev = e, e = e.next) {
            if (e.hash == hash && e.key.equals(key)) {
                V newValue = remappingFunction.apply(e.value, value);
                if (newValue == null) {
                    modCount++;
                    if (prev != null) {
                        prev.next = e.next;
                    } else {
                        tab[index] = e.next;
                    }
                    count--;
                } else {
                    e.value = newValue;
                }
                return newValue;
            }
        }

        if (value != null) {
            addEntry(hash, key, value, index);
        }

        return value;
    }

    /**
     * 将Hashtable的状态保存进流中
     * @serialData The <i>capacity</i> of the Hashtable (the length of the
     *             bucket array) is emitted (int), followed by the
     *             <i>size</i> of the Hashtable (the number of key-value
     *             mappings), followed by the key (Object) and value (Object)
     *             for each key-value mapping represented by the Hashtable
     *             The key-value mappings are emitted in no particular order.
     */
    private void writeObject(java.io.ObjectOutputStream s)
            throws IOException {
        Entry<Object, Object> entryStack = null;

        synchronized (this) {
            // Write out the length, threshold, loadfactor
            s.defaultWriteObject();

            // Write out length, count of elements
            s.writeInt(table.length);
            s.writeInt(count);

            // Stack copies of the entries in the table
            for (int index = 0; index < table.length; index++) {
                Entry<?,?> entry = table[index];

                while (entry != null) {
                    entryStack =
                        new Entry<>(0, entry.key, entry.value, entryStack);
                    entry = entry.next;
                }
            }
        }

        // Write out the key/value objects from the stacked entries
        while (entryStack != null) {
            s.writeObject(entryStack.key);
            s.writeObject(entryStack.value);
            entryStack = entryStack.next;
        }
    }

    /**
     * 从流中重建Hashtable（反序列化）
     */
    private void readObject(java.io.ObjectInputStream s)
         throws IOException, ClassNotFoundException
    {
        // Read in the length, threshold, and loadfactor
        s.defaultReadObject();

        // Read the original length of the array and number of elements
        int origlength = s.readInt();
        int elements = s.readInt();

        // Compute new size with a bit of room 5% to grow but
        // no larger than the original size.  Make the length
        // odd if it's large enough, this helps distribute the entries.
        // Guard against the length ending up zero, that's not valid.
        int length = (int)(elements * loadFactor) + (elements / 20) + 3;
        if (length > elements && (length & 1) == 0)
            length--;
        if (origlength > 0 && length > origlength)
            length = origlength;
        table = new Entry<?,?>[length];
        threshold = (int)Math.min(length * loadFactor, MAX_ARRAY_SIZE + 1);
        count = 0;

        // Read the number of elements and then all the key/value objects
        for (; elements > 0; elements--) {
            @SuppressWarnings("unchecked")
                K key = (K)s.readObject();
            @SuppressWarnings("unchecked")
                V value = (V)s.readObject();
            // synch could be eliminated for performance
            reconstitutionPut(table, key, value);
        }
    }

    /**
     * readObject使用的put方法（重建put），因为put方法支持重写，并且子类尚未初始化的时候不能调用put方法，所以就提供了reconstitutionPut
     * 它和常规put方法有几点不同，不检测rehash,因为初始元素数目已知。modCount不会自增，因为我们是在创建一个新的实例。
     * 不需要返回值
     */
    private void reconstitutionPut(Entry<?,?>[] tab, K key, V value)
        throws StreamCorruptedException
    {
        if (value == null) {
            throw new java.io.StreamCorruptedException();
        }
        // 确保Key不在Hashtable中
        // 反序列化过程中不应该 会发生的情况
        int hash = key.hashCode();
        int index = (hash & 0x7FFFFFFF) % tab.length;
        for (Entry<?,?> e = tab[index] ; e != null ; e = e.next) {
        		//反序列化过程中如果出现Key值重复，抛出异常StreamCorruptedException
            if ((e.hash == hash) && e.key.equals(key)) {
                throw new java.io.StreamCorruptedException();
            }
        }
        // 创建新的Entry.
        @SuppressWarnings("unchecked")
            Entry<K,V> e = (Entry<K,V>)tab[index];
        tab[index] = new Entry<>(hash, key, value, e);
        count++;
    }

    /**
     * Hashtable使用单向链表Entry解决哈希冲突
     */
    private static class Entry<K,V> implements Map.Entry<K,V> {
        final int hash;	//哈希值，不可变
        final K key;	//关键字，不可变
        V value;		//值，可变
        Entry<K,V> next;

        protected Entry(int hash, K key, V value, Entry<K,V> next) {
            this.hash = hash;
            this.key =  key;
            this.value = value;
            this.next = next;
        }
      //返回一个自身的复制对象，浅拷贝，因为还是引用的当前key和value对象，没有新建key和value对象
        @SuppressWarnings("unchecked")
        protected Object clone() {
            return new Entry<>(hash, key, value,
                                  (next==null ? null : (Entry<K,V>) next.clone()));
        }
        // Map.Entry 操作
        public K getKey() {
            return key;
        }

        public V getValue() {
            return value;
        }

        public V setValue(V value) {
        		//Hashtable不允许空null值
            if (value == null)
                throw new NullPointerException();

            V oldValue = this.value;
            this.value = value;
            	//返回原值
            return oldValue;
        }
        	//重写equals方法
        public boolean equals(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?,?> e = (Map.Entry<?,?>)o;
            	//类型相同且键值（key-value）也相同（equals返回true）
            return (key==null ? e.getKey()==null : key.equals(e.getKey())) &&
               (value==null ? e.getValue()==null : value.equals(e.getValue()));
        }

        public int hashCode() {
        		//hash值只与关键字key有关，hashCode需要与值value的hashCode异或
            return hash ^ Objects.hashCode(value);
        }

        public String toString() {
            return key.toString()+"="+value.toString();
        }
    }

    // Enumerations/Iterations的类型
    private static final int KEYS = 0;
    private static final int VALUES = 1;
    private static final int ENTRIES = 2;

    /**
     * Hashtable的枚举类。实现了迭代器和枚举接口，但是去掉迭代器方法也能单独创建实例
     * 这对于避免只通过传递枚举类型来提升容量的意外情况很重要
     */
    private class Enumerator<T> implements Enumeration<T>, Iterator<T> {
        Entry<?,?>[] table = Hashtable.this.table;	//由Hashtable的table数组支持
        int index = table.length;		//table数组的长度
        Entry<?,?> entry = null;		//下一个返回元素
        Entry<?,?> lastReturned = null;	//上一次返回元素
        int type;	//类型：KEYS,Values，Entries

        /**
         * 表明当前枚举是作为一个迭代器还是一个枚举类型（true表示迭代器）
         */
        boolean iterator;

        /**
         * 迭代器认为Hashtable应该拥有的modCount值。如果期望的不一致，迭代器就检测到并发修改了
         */
        protected int expectedModCount = modCount;
        /**
         * 构造函数：构造一个类型为type的迭代器或枚举集合（iterator为true表示迭代器）
         * @param type
         * @param iterator
         */
        Enumerator(int type, boolean iterator) {
            this.type = type;
            this.iterator = iterator;
        }
        	//是否还有更多元素
        public boolean hasMoreElements() {
            Entry<?,?> e = entry;
            int i = index;
            Entry<?,?>[] t = table;
            /* 使用本地变量可以使迭代循环的更快*/
            	//上一个返回元素为空，表明从头开始返回
            while (e == null && i > 0) {
            		//table数组从后向前遍历，找到第一个非空元素
                e = t[--i];
            }
            entry = e;
            index = i;
            return e != null;
        }
        	//返回下一个元素
        @SuppressWarnings("unchecked")
        public T nextElement() {
            Entry<?,?> et = entry;
            int i = index;
            Entry<?,?>[] t = table;
            /* 使用本地变量可以使循环迭代得更快 */
            	//上一个返回元素为空，表明开始返回第一个元素
            while (et == null && i > 0) {
            		//table数组从后向前遍历，找到第一个非空元素
                et = t[--i];
            }
            entry = et;
            index = i;	//更新Index为当前返回的最大i
            if (et != null) {
                Entry<?,?> e = lastReturned = entry;	//更新上一个返回元素为当前即将返回的元素
                entry = e.next;		//更新下一个返回元素为e.next
                	//类型为keys则返回Key，为value则返回value，否则返回Entry
                return type == KEYS ? (T)e.key : (type == VALUES ? (T)e.value : (T)e);
            }
            	//抛出找不到元素异常
            throw new NoSuchElementException("Hashtable Enumerator");
        }

        // 迭代器方法
        public boolean hasNext() {
            return hasMoreElements();
        }
        	//返回下一个元素
        public T next() {
        		//首先检测并发修改异常
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            	//调用nextElement方法
            return nextElement();
        }
        	//删除函数，删除的是上一个返回元素lastReturned
        public void remove() {
        		//只有迭代器类型支持该函数 ,否则抛出不支持该操作异常UnsupportedOperationException()
            if (!iterator)
                throw new UnsupportedOperationException();
            	//如果上一个返回元素为空，抛出非法状态异常IllegalStateException
            if (lastReturned == null)
                throw new IllegalStateException("Hashtable Enumerator");
            	//检测并发修改异常ConcurrentModificationException
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            	//删除时，需要锁住全表
            synchronized(Hashtable.this) {
                Entry<?,?>[] tab = Hashtable.this.table;
                	//上一个返回元素的哈希值最高位之外的所有位模table的长度
                int index = (lastReturned.hash & 0x7FFFFFFF) % tab.length;

                @SuppressWarnings("unchecked")
                	//获取该槽位第一个元素
                Entry<K,V> e = (Entry<K,V>)tab[index];
                	//从单链表的一端向后遍历
                for(Entry<K,V> prev = null; e != null; prev = e, e = e.next) {
                		//当前元素即为上一个返回元素
                    if (e == lastReturned) {
                        modCount++;
                        expectedModCount++;
                        	//删除上一个元素
                        if (prev == null)
                            tab[index] = e.next;
                        else
                            prev.next = e.next;
                        count--;
                        lastReturned = null;
                        return;
                    }
                }
                throw new ConcurrentModificationException();
            }
        }
    }
}
