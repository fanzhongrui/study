###**1. Template Method**
####**1.1 Arrays.sort()**
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
//算法框架  
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
