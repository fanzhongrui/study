###**编程习惯**
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
/* * Returns an unmodifiable view of the specified map. This method * allows modules to provide users with “read-only” access to internal * maps. Query operations on the returned map “read through” * to the specified map, and attempts to modify the returned * map, whether direct or via its collection views, result in an * UnsupportedOperationException.
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
