##**1. 迭代器与组合模式**
###**1.1 Collection.iterator()**
集合（Collection）指的是一群对象，其存储方式可以是各式各样的数据结构。  
如何能让客户遍历你的对象而又无法窥视你存储对象的方式——利用迭代器（iterator）`java.util.Iterator`来封装“遍历集合内的每个对象的过程”。

```java
//忽略集合实现方式（列表、数组、散列表）封装对象遍历
public interface Iterator<E> {
    boolean hasNext();
    E next();
    default void remove() {
        throw new UnsupportedOperationException("remove");
    }
    default void forEachRemaining(Consumer<? super E> action) {
        Objects.requireNonNull(action);
        while (hasNext())
            action.accept(next());
    }
}
```

###**1.2  java.util.Enumeration**
枚举类型Enumeration提供与iterator类似的功能

```java
public interface Enumeration<E> {
    boolean hasMoreElements();
    E nextElement();
}
```

##**2. 模板方法模式**
###**2.1 java.util.AbstractList, java.util.AbstractSet和java.util.AbstractMap的所有非抽象方法**
```java
package java.util;
public abstract class AbstractList<E> extends AbstractCollection<E> implements List<E> {
    protected AbstractList() {
    }
    public boolean add(E e) {
        add(size(), e);
        return true;
    }
    public E set(int index, E element) {
        throw new UnsupportedOperationException();
    }
    public void add(int index, E element) {
        throw new UnsupportedOperationException();
    }
    public E remove(int index) {
        throw new UnsupportedOperationException();
    }
     // Search Operations
    public int indexOf(Object o) {
        ListIterator<E> it = listIterator();
        if (o==null) {
            while (it.hasNext())
                if (it.next()==null)
                    return it.previousIndex();
        } else {
            while (it.hasNext())
                if (o.equals(it.next()))
                    return it.previousIndex();
        }
        return -1;
    }
    public int lastIndexOf(Object o) {
        ListIterator<E> it = listIterator(size());
        if (o==null) {
            while (it.hasPrevious())
                if (it.previous()==null)
                    return it.nextIndex();
        } else {
            while (it.hasPrevious())
                if (o.equals(it.previous()))
                    return it.nextIndex();
        }
        return -1;
    }
    public void clear() {
        removeRange(0, size());
    }
    public boolean addAll(int index, Collection<? extends E> c) {
        rangeCheckForAdd(index);
        boolean modified = false;
        for (E e : c) {
            add(index++, e);
            modified = true;
        }
        return modified;
    }
    // Iterators
    public Iterator<E> iterator() {
        return new Itr();
    }
    public ListIterator<E> listIterator() {
        return listIterator(0);
    }
    public ListIterator<E> listIterator(final int index) {
        rangeCheckForAdd(index);
        return new ListItr(index);
    }
    public List<E> subList(int fromIndex, int toIndex) {
        return (this instanceof RandomAccess ?
                new RandomAccessSubList<>(this, fromIndex, toIndex) :
                new SubList<>(this, fromIndex, toIndex));
    }
    // Comparison and hashing
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof List))
            return false;

        ListIterator<E> e1 = listIterator();
        ListIterator<?> e2 = ((List<?>) o).listIterator();
        while (e1.hasNext() && e2.hasNext()) {
            E o1 = e1.next();
            Object o2 = e2.next();
            if (!(o1==null ? o2==null : o1.equals(o2)))
                return false;
        }
        return !(e1.hasNext() || e2.hasNext());
    }
    public int hashCode() {
        int hashCode = 1;
        for (E e : this)
            hashCode = 31*hashCode + (e==null ? 0 : e.hashCode());
        return hashCode;
    }
    protected void removeRange(int fromIndex, int toIndex) {
        ListIterator<E> it = listIterator(fromIndex);
        for (int i=0, n=toIndex-fromIndex; i<n; i++) {
            it.next();
            it.remove();
        }
    }
    protected transient int modCount = 0;

    private void rangeCheckForAdd(int index) {
        if (index < 0 || index > size())
            throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
    }

    private String outOfBoundsMsg(int index) {
        return "Index: "+index+", Size: "+size();
    }
}
```

##**1. Template Method**
###**1.1 Arrays.sort()**
```java
// Arrays
public static void sort(Object[] a) {
…
ComparableTimSort.sort(a);
}
// ComparableTimSort
static void sort(Object[] a, int lo, int hi) {
…
binarySort(a, lo, hi, lo + initRunLen);
}
```
```java
//算法框架在工具类中实现
    private static void binarySort(Object[] a, int lo, int hi, int start) {
        assert lo <= start && start <= hi;
        if (start == lo)
            start++;
        for ( ; start < hi; start++) {
            @SuppressWarnings("unchecked")
            Comparable<Object> pivot = (Comparable) a[start];

            // Set left (and right) to the index where a[start] (pivot) belongs  
            ....
            while (left < right) {
                int mid = (left + right) >>> 1;
                if (pivot.compareTo(a[mid]) < 0) //compareTo这个算法步骤，是由各个Comparable的子类定义的  
                    right = mid;
                else
                    left = mid + 1;
            }
            ....
        }
    }
```
###**1.2 InputStream.read()**
```java
//算法框架  
 public int read(byte b[], int off, int len) throws IOException {
        ...

        int c = read();

        ...
}
//算法步骤由子类实现  
public abstract int read() throws IOException;
```

###**1.3 JFrame.paint()**

```java
// JFrame  
    public void update(Graphics g) {
        paint(g);
    }

public class MyFrame extends JFrame {
    public MyFrame(){
        super();
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setSize(300,300);
        this.setVisible(true);
    }

    @Override
    public void paint(Graphics g){ //重定义算法步骤,实现自定义的paint()方法
        super.paint(g);
        g.drawString("I rule !", 100, 100);
    }

    public static void main(String[] args){
        MyFrame frame = new MyFrame();
    }

}
```
###**1.4 Applet.init()/start()/stop()/destroy()/paint()——————模板方法模式**

Applet中的init()/start()/stop()/destroy()/paint()这些方法，都是hook
```java
// Applet  
    public void init() { //什么也不做的hook  
    }

// Beans  
   public static Object instantiate(ClassLoader cls, String beanName,
                    BeanContext beanContext, AppletInitializer initializer)
                        throws IOException, ClassNotFoundException {



                // If it was deserialized then it was already init-ed.  
                // Otherwise we need to initialize it.  

                if (!serialized) {
                    // We need to set a reasonable initial size, as many  
                    // applets are unhappy if they are started without  
                    // having been explicitly sized.  
                    applet.setSize(100,100);
                    applet.init(); //调用hook  
                }


        }

        return result;
    }
```

