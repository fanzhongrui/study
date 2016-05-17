##**1. Iterator迭代器————————迭代器与组合模式**
###**1.1 Collection.iterator()**
如何能让客户遍历你的对象而又无法窥视你存储对象的方式
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

