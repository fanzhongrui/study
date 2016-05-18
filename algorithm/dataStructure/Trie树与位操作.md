使用Trie树解决位操作问题。
##**Trie树**
Trie树可以将keys/numbers/strings等信息保存在树中。  
Trie树由一系列结点组成，每个结点存储一个字符/位。从而我们可以插入新的strings/numbers。  
以trie树存储strings为例：    
![trie树]()  

但现在我们要用trie树解决数字问题，特别是二进制位。问题如下：  

>Problem1: Given an array of integers, we have to find two elements whose XOR is maximum.  

Solution：
假设有一种数据结构可以满足两种查询操作：
+1.插入一个数值X  
+2.给定Y，找到目前已经插入的所有数据中与Y相异或（XOR）的最大值    
 如果有这样的数据结构，那就依次插入数据并查询最大值，从而获得最终的最大值。  
 trie树就是我们将要使用的数据结构。  
 首先，看看如何在trie树中插入元素。
