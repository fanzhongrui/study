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
        int cursor = 0, numDigits;
        final int len = val.length();

        if (radix < Character.MIN_RADIX || radix > Character.MAX_RADIX)
            throw new NumberFormatException("Radix out of range");
        if (len == 0)
            throw new NumberFormatException("Zero length BigInteger");

        // Check for at most one leading sign
        int sign = 1;
        int index1 = val.lastIndexOf('-');
        int index2 = val.lastIndexOf('+');
        if (index1 >= 0) {
            if (index1 != 0 || index2 >= 0) {
                throw new NumberFormatException("Illegal embedded sign character");
            }
            sign = -1;
            cursor = 1;
        } else if (index2 >= 0) {
            if (index2 != 0) {
                throw new NumberFormatException("Illegal embedded sign character");
            }
            cursor = 1;
        }
        if (cursor == len)
            throw new NumberFormatException("Zero length BigInteger");

        // Skip leading zeros and compute number of digits in magnitude
        while (cursor < len &&
               Character.digit(val.charAt(cursor), radix) == 0) {
            cursor++;
        }

        if (cursor == len) {
            signum = 0;
            mag = ZERO.mag;
            return;
        }

        numDigits = len - cursor;
        signum = sign;

        // Pre-allocate array of expected size. May be too large but can
        // never be too small. Typically exact.
        long numBits = ((numDigits * bitsPerDigit[radix]) >>> 10) + 1;
        if (numBits + 31 >= (1L << 32)) {
            reportOverflow();
        }
        int numWords = (int) (numBits + 31) >>> 5;
        int[] magnitude = new int[numWords];

        // Process first (potentially short) digit group
        int firstGroupLen = numDigits % digitsPerInt[radix];
        if (firstGroupLen == 0)
            firstGroupLen = digitsPerInt[radix];
        String group = val.substring(cursor, cursor += firstGroupLen);
        magnitude[numWords - 1] = Integer.parseInt(group, radix);
        if (magnitude[numWords - 1] < 0)
            throw new NumberFormatException("Illegal digit");

        // Process remaining digit groups
        int superRadix = intRadix[radix];
        int groupVal = 0;
        while (cursor < len) {
            group = val.substring(cursor, cursor += digitsPerInt[radix]);
            groupVal = Integer.parseInt(group, radix);
            if (groupVal < 0)
                throw new NumberFormatException("Illegal digit");
            destructiveMulAdd(magnitude, superRadix, groupVal);
        }
        // Required for cases where the array was overallocated.
        mag = trustedStripLeadingZeroInts(magnitude);
        if (mag.length >= MAX_MAG_LENGTH) {
            checkRange();
        }
    }
```
####**2.不可变对象**
#####**2.1共享不可变对象内部信息：BigInteger.negate():**
>**可以自由地共享不可变对象，还可以共享它们的内部信息**
比如：BigInteger类内部使用了一个符号数值表示法（sign-magnitude representation），符号用一个int表示，数值则用一个int数组表示。
`negate()`方法会创建一个数值相同但符号相反的新`BigInteger`，该方法不需要拷贝数组，新创建的BigInteger只需要指向源对象中的数组即可。
```java
/**
 * Returns a BigInteger whose value is {@code (-this)}.
 *
 * @return {@code -this}
 */
public BigInteger negate() {
      return new BigInteger(this.mag, -this.signum);
}
```
#####**2.2鼓励重用现有不可变实例：BigInteger.ZERO**
**不可变对象生来就是线程安全的，他们不需要同步。**当多个线程并发访问不可变对象时，他们不会遭到破坏。
这无疑是实现线程安全的最容易的方法。实际上，不会有线程能观察到其他线程对不可变对象的影响。所以不可变对象可以被自由地共享。  

不可变类应当利用这种优势，鼓励客户端尽可能重用现有实例。一个简单的方法是为常用的值提供public static final的常量。
```java
public static final BigInteger ZERO = new BigInteger(new int[0], 0);
public static final BigInteger ONE = valueOf(1);
public static final BigInteger TEN = valueOf(10);
```
####**3.为不可变类提供companion class：String vs StringBuilder**
不可变类的一个缺点是，对于每个不同的值都需要一个单独的对象。
那么在执行复杂的多步操作时，每一步都会创建一个新的对象。  
通过提供`companion class`可以解决这个问题。例如当需要对`String`执行复杂操作时，建议使用`StringBuilder`。

####**4.不可变类中可以用nonfinal域，用于存储缓存：string.hashCode()**
不可变类所有域必须是final的。实际上这些规则比较强硬，为了提供性能可以有所放松。
实际上应该是没有方法能够对类的状态产生外部可见的改变（no method may produce an externally visible change in the object’s state）。

然而，一些不可变类拥有一个或多个nonfinal域，用于缓存昂贵计算的结果。这个技巧可以很好地工作，因为对象是不可变的，保证了相同的计算总是返回同样的结果。
```java
/** Cache the hash code for the string */
private int hash; // Default to 0
public int hashCode() {
  int h = hash;
  int len = count;
  if (h == 0 && len > 0) {
    int off = offset;
    char val[] = value;
        for (int i = 0; i < len; i++) {
            h = 31*h + val[off++];
        }
        hash = h;
  }
  return h;
}
```
####**5.用工厂方法替代构造函数**
#####**5.1 Boolean.valueOf()：**
通过一个boolean简单类型，构造Boolean对象引用  哪些实例存在**    

>优点：**无需每次被调用时都创建一个新对象。同时使得类可以严格控制在哪个时刻有
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
##### **5.2 BigInteger.probablePrime()**:
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
#####**5.3 EnumSet**:  
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
#####**5.4 Collections.unmodifiableMap(Map)**  
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
###6.**私有化构造函数，实现Singleton**
####6.1**Arrays**  
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
###7.**消除无用的对象引用**
####7.1**LinkedHashMap.removeEldestEntry()**
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

###8.**避免创建不必要的对象**
####8.1**Map.keySet()**
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
