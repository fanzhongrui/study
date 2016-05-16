####**1.如果指定了toString()返回值的格式，则应该提供一个对应的静态工厂方法**
#####**1.1BigInteger.toString()**
```java
/* * Returns the String representation of this BigInteger in the * given radix.* / 
public String toString(int radix) {
/**
 * Returns the decimal String representation of this BigInteger.
 */
public String toString() {
    return toString(10);
}
```
对应的构造函数如下：
```java
/**
 * Translates the decimal String representation of a BigInteger into a
 * BigInteger.  
 */
public BigInteger(String val) {
    this(val, 10);
}
/**
 * Translates the String representation of a BigInteger in the specified
 * radix into a BigInteger.  
 */
public BigInteger(String val, int radix) { 
```
####**1.用工厂方法替代构造函数**
#####**1.1 Boolean.valueOf()：**
通过一个boolean简单类型，构造Boolean对象引用  
>优点：**无需每次被调用时都创建一个新对象。同时使得类可以严格控制在哪个时刻有哪些实例存在**    

```java
/**
 * Returns a <code>Boolean</code> with a value represented by the
 * specified string.  The <code>Boolean</code> returned represents a
 * true value if the string argument is not <code>null</code>
 * and is equal, ignoring case, to the string {@code "true"}.
 *
 * @param   s   a string.
 * @return  the <code>Boolean</code> value represented by the string.
 */
public static Boolean valueOf(String s) {
    return toBoolean(s) ? TRUE : FALSE;
}
```
静态工厂方法Boolean.valueOf(String)几乎总是比构造函数Boolean(String)更可取。
构造函数每次被调用时都会创建一个新对象，而静态工厂方法则从来不要求这样做，实际上也不会这么做。
##### **1.2 BigInteger.probablePrime()**:
构造方法BigInteger(int, int, Random)返回一个可能为素数的BigInteger，而用一个名为BigInteger.probablePrime()的静态工厂方法会更好。
>优点：**方法名对客户端更友好**  

```java
public class BigInteger extends Number implements Comparable<BigInteger> {
   /**
     * Returns a positive BigInteger that is probably prime, with the
     * specified bitLength. The probability that a BigInteger returned
     * by this method is composite does not exceed 2<sup>-100</sup>.
     *
     */
    public static BigInteger probablePrime(int bitLength, Random rnd) {
    if (bitLength < 2)
        throw new ArithmeticException("bitLength < 2");
        // The cutoff of 95 was chosen empirically for best performance
        return (bitLength < SMALL_PRIME_THRESHOLD ?
                smallPrime(bitLength, DEFAULT_PRIME_CERTAINTY, rnd) :
                largePrime(bitLength, DEFAULT_PRIME_CERTAINTY, rnd));
    }
```
#####**1.3 EnumSet**:  
JDK1.5引入的`java.util.EnumSet`类没有public构造函数，只有静态工厂方法。  
根据底层枚举类型的大小，这些工厂方法可以返回两种实现：  
-如果用于64个或更少的元素（大多数枚举类型都是这样），静态工厂方法返回一个`RegularEnumSet`实例，用单个long来支持；  
-如果枚举类型拥有65个或更多的元素，静态工厂方法则返回`JumboEnumSet`实例，用`long数组`来支持。    
>优点：**静态工厂方法能返回任意子类型的对象。可以根据参数的不同，而返回不同的类型**  

```java
public abstract class EnumSet<E extends Enum<E>> extends AbstractSet<E>
    implements Cloneable, java.io.Serializable
{
    /**
     * Creates an empty enum set with the specified element type.
     *
     * @param elementType the class object of the element type for this enum
     *     set
     * @throws NullPointerException if <tt>elementType</tt> is null
     */
    public static <E extends Enum<E>> EnumSet<E> noneOf(Class<E> elementType) {
        Enum[] universe = getUniverse(elementType);
        if (universe == null)
            throw new ClassCastException(elementType + " not an enum");
        if (universe.length <= 64)
            return new RegularEnumSet<E>(elementType, universe);
        else
            return new JumboEnumSet<E>(elementType, universe);
    }
}
class RegularEnumSet<E extends Enum<E>> extends EnumSet<E> {
}
class JumboEnumSet<E extends Enum<E>> extends EnumSet<E> {
}
 ```
#####**1.4 Collections.unmodifiableMap(Map)**  
Java集合框架中有32个集合接口的便利实现，提供不可修改的集合、同步集合等等。几乎所有的实现都通过一个不可实例化类（`java.util.Collections`）中的静态工厂方法导出，返回对象的类都是非public的。

> 优点：**静态工厂方法能返回任意子类型的对象。可以返回一个对象而无需使相应的类public。用这种方式隐藏实现类能够产生一个非常紧凑的API**  

```java
public class Collections { 
/* * Returns an unmodifiable view of the specified map. This method * allows modules to provide users with “read-only” access to internal * maps.        
Query operations on the returned map “read through” * to the specified map, and attempts to modify the returned * map, whether direct or via its collection views, result in an * UnsupportedOperationException.
* / 
public static <K,V> Map<K,V> unmodifiableMap(Map<? extends K, ? extends V> m) { return new UnmodifiableMap<K,V>(m); }
private static class UnmodifiableMap<K,V> implements Map<K,V>, Serializable {
  }
```
###2.**私有化构造函数，实现Singleton**
####2.1**Arrays**  
这种工具类设计出来并不是为了实例化它，然而，
如果不显示地编写构造函数，编译器则会提供一个公共的无参数的默认构造方法。
所以将构造函数私有化：  
```java
public class Arrays {    
// Suppresses default constructor, ensuring non-instantiability.
    private Arrays() { } 
    }
``` 
还可以在这个私有构造器内部加上`throw new AssertionError()`，可以确保该方法不会在类内部被意外调用。
这种习惯用法的副作用是类不能被子类化了。子类的所有构造函数必须首先隐式或显式调用父类构造函数，而在这种用法下，
子类就没有可访问的父类构造函数可调用了。  
####2.2TimeUnit
`java.util.concurrent.TimeUnit`使用枚举来实现Singleton：
```java
public enum TimeUnit {
    MILLISECONDS {
        public long toNanos(long d)   { return x(d, C2/C0, MAX/(C2/C0)); }
        public long toMicros(long d)  { return x(d, C2/C1, MAX/(C2/C1)); }
        public long toMillis(long d)  { return d; }
        public long toSeconds(long d) { return d/(C3/C2); }
        public long toMinutes(long d) { return d/(C4/C2); }
        public long toHours(long d)   { return d/(C5/C2); }
        public long toDays(long d)    { return d/(C6/C2); }
        public long convert(long d, TimeUnit u) { return u.toMillis(d); }
        int excessNanos(long d, long m) { return 0; }
    },
    SECONDS {
       .....
    },
    MINUTES {
       .....
    },
    HOURS {
       ......
    },
    DAYS {
       .....
    };
    // TimeUnit.sleep()用来替代Thread.sleep()
    public void sleep(long timeout) throws InterruptedException {
        if (timeout > 0) {
            long ms = toMillis(timeout);
            int ns = excessNanos(timeout, ms);
            Thread.sleep(ms, ns);
        }
    }
```
###3.**消除无用的对象引用**
####3.1**LinkedHashMap.removeEldestEntry()**
缓存实体的生命周期不容易确定，随着时间推移，实体的价值越来越低。在这种情况下，缓存应该不定期地清理无用的实体。
可以通过一个后台线程来清理（可能是`Timer`或`ScheduledThreadPoolExecutor`），也可以在给缓存添加新实体时进行清理。  
LikedHashMap可利用其removeEldestEntry，删除较老的实体：
```java
public class LinkedHashMap<K,V> extends HashMap<K,V> implements Map<K,V> { 
/ * It causes newly allocated entry to get inserted at the end of the linked list and 
  * removes the eldest entry if appropriate. 
  */ 
  void addEntry(int hash, K key, V value, int bucketIndex) { 
        createEntry(hash, key, value, bucketIndex); 
        // Remove eldest entry if instructed, else grow capacity if appropriate 
        Entry<K,V> eldest = header.after; 
        if (removeEldestEntry(eldest)) { 
            removeEntryForKey(eldest.key); 
        } else { 
            if (size >= threshold) resize(2 * table.length);
        }
    } 
    / * Returns true if this map should remove its eldest entry. 
      * This method is invoked by put and putAll after 
      * inserting a new entry into the map.
    * * Sample use: *
     *     private static final int MAX_ENTRIES = 100;
     *
     *     protected boolean removeEldestEntry(Map.Entry eldest) {
     *        return size() > MAX_ENTRIES;
     *     }
     * 
* */ 
protected boolean removeEldestEntry(Map.Entry<K,V> eldest) { return false; }
```
可以继承LinkedHashMap，覆盖其removeEldestEntry方法。
>如果想要缓存中的对象只要不被引用，就自动清理，可以用WeakHashMap  

###4.**避免创建不必要的对象**
####4.1**Map.keySet()**
Map接口的keySet()方法返回Map对象的一个Set视图，包含该Map的所有key。看起来好像每次调用keySet()都需要创建一个新的Set实例。
而实际上，虽然返回的Set通常是可变的，但返回的对象在功能上是等同的：如果其中一个返回对象改变，其他对象也会改变，因为他们的底层都是同一个Map实例。虽然创建多个KeySet视图对象没有害处，但是没必要。
```java
public abstract class AbstractMap<K,V> implements Map<K,V> {

    /**
     * Each of these fields are initialized to contain an instance of the
     * appropriate view the first time this view is requested.  The views are
     * stateless, so there's no reason to create more than one of each.
     */
   transient volatile Set<K>        keySet = null;
   public Set<K> keySet() {
    if (keySet == null) {
        keySet = new AbstractSet<K>() {
        .....
        };
    }
    return keySet;
    }
}
```
在构造keySet之前，对其进行了null检查，只有当它是null时才会初始化。
