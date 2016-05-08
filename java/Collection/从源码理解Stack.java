package java.util;

/**
 * Stack类表示了后进先出（LIFO）的一个容器对象。Stack继承自Vector并扩展了五个操作，使得Vector可以被看作是一个Stack。
 * 常用的push和pop，以及获取栈顶元素的peek，测试栈是否为空的empty，一个搜索操作search并返回其与栈顶的距离
 * 第一次创建的时候，栈中没有元素
 * 更丰富更兼容的LIFO操作由Deque接口提供，Deque使用起来比Stack更好，比如：
 *  Deque<Integer> stack = new ArrayDeque<Integer>();
 */
public class Stack<E> extends Vector<E> {
    /**
     * 构造一个空的Stack
     */
    public Stack() {
    }

    /**
     * 向栈顶压入元素，和addElement(item)等效
     * @param   item   the item to be pushed onto this stack.
     * @return  the <code>item</code> argument.
     * @see     java.util.Vector#addElement
     */
    public E push(E item) {
    		//调用Vector的addElement
        addElement(item);

        return item;
    }

    /**
     * 移除并返回栈顶元素
     * @return  The object at the top of this stack (the last item
     *          of the <tt>Vector</tt> object).
     * @throws  EmptyStackException  if this stack is empty.
     */
    public synchronized E pop() {
        E       obj;
        int     len = size();
        	//获取栈顶元素
        obj = peek();
        	//移除元素
        removeElementAt(len - 1);

        return obj;
    }

    /**
     * 不移除，只返回栈顶元素
     * @return  the object at the top of this stack (the last item
     *          of the <tt>Vector</tt> object).
     * @throws  EmptyStackException  if this stack is empty.
     */
    public synchronized E peek() {
        int     len = size();

        if (len == 0)
            throw new EmptyStackException();
        return elementAt(len - 1);
    }

    /**
     * 测试是否为空
     * @return  <code>true</code> if and only if this stack contains
     *          no items; <code>false</code> otherwise.
     */
    public boolean empty() {
        return size() == 0;
    }

    /**
     * 返回指定元素与栈顶元素的最近距离，对象比较使用equals方法，栈顶元素为坐标起点1
     * @param   o   the desired object.
     * @return  the 1-based position from the top of the stack where
     *          the object is located; the return value <code>-1</code>
     *          indicates that the object is not on the stack.
     */
    public synchronized int search(Object o) {
    		//获取指定元素的下标
        int i = lastIndexOf(o);
        	//size-i为栈顶与当前元素的距离
        if (i >= 0) {
            return size() - i;
        }
        return -1;
    }

    /** use serialVersionUID from JDK 1.0.2 for interoperability */
    private static final long serialVersionUID = 1224463164541339165L;
}
