Java锁机制：Synchronized，Lock，Condition
=========================================
####1、synchronized
把代码块声明为synchronized，有两个重要后果，通常是指该代码具有原子性（automicity）和可见性（visibility）。

#####原子性
原子性意味着某个时刻，只有一个线程能够执行一段代码，这段代码通过一个monitor Object保护。从而防止多个线程在更新共享状态时相互冲突。

#####可见性
可见性要对付内存缓存和编译器优化的各种反常行为。它必须确保释放锁之前对共享数据做出的更改对于随后获得该锁的另一个线程是可见的。

**作用**：如果没有同步机制提供这种可见性保证，线程看到的共享变量可能是修改前的值或不一致的值，这将引发许多严重问题。
  
**原理**：当对象获取锁时，它首先使自己的高速缓存无效，这样就可以保证直接从主内存中装入变量。同样，在对象释放锁之前，它会刷新其高速缓存 ，
  
强制使已做的任何更改都出现在主内存中。这样，可以保证在同一个锁上同步的两个线程看到在synchronized块内修改的变量的相同值。

一般来说，线程以某种不必让其他线程立即可以看到的方式（不管这些线程在寄存器中、在处理器特定的缓存中，还是通过指令重排或者其他编译器优化），不受缓存变量值的约束，但是如果开发人员使用了同步，那么运行库将确保某一线程对变量所做的更新先于对现有synchronized块所进行的更新，当进入由同一监控器（lock）保护的另一个synchronized块时，将立刻可以看到这些对变量所做的更新。
  
类似的规则也存在与volatile变量上————————**volatile只保证可见性，不保证原子性**。

#####需要同步的场景
**可见性同步**的基本规则是在以下情况中必须同步：

>1.读取上一次可能是由另一个线程写入的变量  
2.写入下一次可能由另一个线程读取的变量

**一致性同步**：当修改多个相关值时，你想要其他线程原子地看到这组更改——要么看到全部更改，要么什么也看不到。

这适用于相关数据项（如粒子的位置和速率）和元数据项（如链表中包含的数据值和列表自身中的数据项的链）

但在某些情况中，不必用同步来将数据从一个线程传递到另一个，因为JVM已经隐含地为您执行同步。这些情况包括：

>1.由静态初始化器（在静态字段上或static{}块中的初始化器）  
2.初始化数据时  
3.访问final字段时  
4.在创建线程之前创建对象时  
5.线程可以看见它将要处理的对象  

#####synchronized的限制
synchronized有一些功能上的限制：
>1.它无法中断一个正在等候获得锁的线程  
2.无法通过投票得到锁，如果不想等下去，也就没法得到锁  
3.同步还要求锁的释放只能在与获得锁所在的堆栈帧相同的堆栈帧中进行，多数情况下，这没问题（而且与异常处理交互的很好），但是，确实存在一些非块结构的锁更合适的情况

####ReentrantLock
Java.util.concurrent.lock的Lock框架是锁定的一个抽象，它允许把锁定的实现作为Java类，而不是作为语言的特性来实现。
这为Lock的多种实现留下了空间，各种实现可能有不同的调度算法、性能特性或者锁定语义。  
ReentrantLock类实现了Lock，它拥有与synchronized相同的并发性和内存语义，但是添加了类似投票、定时锁等候和可中断锁等候的一些特性。  
它还提供了在激烈争用情况下更佳的性能。即，当许多线程都想访问共享资源时，JVM可以花更少的时间来调度线程，把更多的时间用在执行线程上。  
注意：用synchronized修饰的方法或者语句块在代码执行完之后锁自动释放，而Lock类需要手动释放，所以为了保证锁最终被释放（发生异常情况时），要把互斥区放在try内，释放锁放在finally内！  
####读写锁ReadWriteLock
与互斥锁相比，读-写锁定允许对共享数据进行更高级别的并发访问。虽然一次只有一个线程（writer线程）可以修改共享数据，但在许多情况下，
任何数量的线程可以同时读取共享数据（reader线程）  
从理论上讲，与互斥锁相比，读-写锁所允许的并发性能增强将带来更大的性能提高。  
只有在多处理器上并且只在访问模式适用于共享数据时，才能实现并发性增强。  
————例如，某个最初用数据填充并且之后不经常对其进行修改的Collection，因为经常对其进行搜索（比如搜索某种目录），所以这样的Collection是使用读-写锁定的理想候选者。  
####线程间通信Condition
Condition可以替代传统的线程间通信，**用await()替换wait()，用signal()替换notify()，用signalAll()替换notifyAll()。**
>为什么方法名不直接叫wait()/notify()/notifyAll()？因为Object的这几个方法是final的，不可重写！  
传统线程的通信方式，Condition都可以实现。  
Condition是被绑定到Lock上的，要创建一个Lock的Condition必须用newCondition()方法。  
Condition的强大在于它可以为多个线程间建立不同的Condition。  
>`package test.concurrent;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class BoundedBuffer {
	final Lock lock = new ReentrantLock();  //锁对象
	final Condition notFull = lock.newCondition(); //写线程锁
	final Condition notEmpty = lock.newCondition();//读线程锁
	
	final Object[] items = new Object[100];   //缓存队列
	int putptr;									//写索引
	int takeptr;							//读索引
	int count;  							//队列中数据数目
	//写
	public void put(Object x)throws InterruptedException{
		lock.lock();	//锁定
		try{
			//若队列已满，则将 写线程 放入阻塞队列
			while(count == items.length){
				notFull.await();
			}
			//写入队列，并更新写索引
			items[putptr] = x;
			if(++putptr == items.length)putptr=0;
			++count;
			//唤醒 读线程
			notEmpty.signal();
		}finally{
			lock.unlock();//解除锁定
		}
	}
	//读
	public Object take() throws InterruptedException{
		lock.lock();	//锁定
		try{
			//若队列为空，则将 读线程 放入阻塞队列
			while(count == 0){
				notEmpty.await();
			}
			//读取队列，并更新读索引
			Object x = items[takeptr];
			if(++takeptr == items.length)takeptr = 0;
			--count;
			//唤醒 写线程
			notFull.signal();
			return x;
		}finally{
			lock.unlock();//解除锁定
		}
	}
}
`
其实就是java.util.concurrent.ArrayBlockingQueue














