##**Buy and Sell Stock系列**

##121.Best Time To Buy and Sell Stock
Say you have an array for which the ith element is the price of a given stock on day i.

If you were only permitted to complete at most one transaction (ie, buy one and sell one share of the stock), design an algorithm to find the maximum profit.

解法一：只能买入卖出一次，可以计算每一天价格与前一天之差，保存在数组a中，使用dp求得数组a中最大连续和即为最大利润。
代码：
```
public int maxProfit(int[] prices) {
        if(prices.length < 2)return 0;
        int[] profit = new int[prices.length];
        for(int i=1; i<prices.length; i++){
            profit[i] = prices[i] - prices[i-1];
        }
        int max = profit[1], ret = profit[1];
        for(int i=2; i<prices.length; i++){
            int temp = max;
            max = (temp+profit[i]) >= profit[i] ? temp+profit[i] : profit[i];
            if(max > ret){
                ret = max;
            }
        }
        return ret>0?ret:0;
    }
```

解法二：动态规划法。从前向后遍历数组，记录当前出现过的最低价格，作为买入价格，并计算以当天价格出售的收益，作为可能的最大收益，整个遍历过程中，出现过的最大收益就是所求。

代码：O(n)时间，O(1)空间
```
public class Solution {
    public int maxProfit(int[] prices) {
        if (prices.length < 2) return 0;
        
        int maxProfit = 0;
        int curMin = prices[0];
        
        for (int i = 1; i < prices.length; i++) {
            curMin = Math.min(curMin, prices[i]);
            maxProfit = Math.max(maxProfit, prices[i] - curMin);
        }
        
        return maxProfit;
    }
}
```
##122. Best Time to Buy and Sell Stock II
Say you have an array for which the ith element is the price of a given stock on day i.

Design an algorithm to find the maximum profit. You may complete as many transactions as you like (ie, buy one and sell one share of the stock multiple times). However, you may not engage in multiple transactions at the same time (ie, you must sell the stock before you buy again).

可以买入卖出任意多次，但任意时刻最多只能拥有至多一只股票，计算每一天价格与前一天之差，保存在数组a中，将所有连续的正值累加，即得最大利润。

>即贪心法。从前向后遍历数组，只要当天的价格高于前一天的价格，就算入收益。

代码：
```
public static int maxProfit(int[] prices){
        int ret = 0;
        for(int i=1; i<prices.length; i++){
            if( prices[i] > prices[i-1] ){
                ret += (prices[i] - prices[i-1]);
            }
        }
        return ret;
}
```

##123. Best Time to Buy and Sell Stock III
Say you have an array for which the ith element is the price of a given stock on day i.

Design an algorithm to find the maximum profit. You may complete at most two transactions.

Note:
You may not engage in multiple transactions at the same time (ie, you must sell the stock before you buy again).

最多交易两次，手上最多只能持有一只股票。

动态规划。以第i天为分界线，计算第i天之前进行一次交易的最大收益preProfit[i]，和第i天之后进行一次交易的最大收益postProfit[i]，最后遍历一遍，max{preProfit[i] + postProfit[i] } (0<= i <= n-1)就是最大收益。

第i天之前和第i天之后进行一次的最大收益求法同Best Time to Buy and Sell Stock I.

代码：时间O(n)，空间O(n)
```
public static int maxProfit(int[] prices){
        if(prices.length<2)return 0;
        int[] pre = new int[prices.length];
        int[] post = new int[prices.length];
        int m = prices[0], max = 0, res = 0;
        for(int i=1; i<prices.length; i++){
            int dif = prices[i]-m;
            if(dif > max){
                max = dif;
            }
            if(prices[i] < m){
                m = prices[i];
            }
            pre[i] = max; 
        }
        
        m = prices[prices.length-1];
        max = 0;
        for(int i=prices.length-2; i>=0; i--){
            int dif = m - prices[i];
            if(dif > max) max = dif;
            if(prices[i] > m) m = prices[i];
            post[i] = max;
        }
        for(int i=0; i<prices.length; i++){
            if(i == 0){
                if(post[i] > res) res = post[i];
            }else if(i == prices.length - 1){
                if(pre[i] > res) res = pre[i];
            }else{
                if(pre[i] + post[i+1] > res){
                    res = pre[i]+post[i+1];
                }
            }
        }
        
        return res;
    }
```
##188. Best Time to Buy and Sell Stock IV
Say you have an array for which the ith element is the price of a given stock on day i.

Design an algorithm to find the maximum profit. You may complete at most k transactions.

Note:
You may not engage in multiple transactions at the same time (ie, you must sell the stock before you buy again).

最多持有k只股票。
分析：特殊动态规划法。传统的动态规划我们会这样想，到第i天时进行j次交易的最大收益，要么等于到第i-1天时进行j次交易的最大收益（第i天价格低于第i-1天的价格），要么等于到第i-1天时进行j-1次交易，然后第i天进行一次交易（第i天价格高于第i-1天价格时）。于是得到动规方程如下（其中diff = prices[i] – prices[i – 1]）：

    profit[i][j] = max(profit[i – 1][j], profit[i – 1][j – 1] + diff)

看起来很有道理，但其实不对，为什么不对呢？因为diff是第i天和第i-1天的差额收益，如果第i-1天当天本身也有交易呢（也就是说第i-1天刚卖出了股票，然后又买入等到第i天再卖出），那么这两次交易就可以合为一次交易，这样profit[i – 1][j – 1] + diff实际上只进行了j-1次交易，而不是最多可以的j次，这样得到的最大收益就小了。

那么怎样计算第i天进行交易的情况的最大收益，才会避免少计算一次交易呢？我们用一个局部最优解和全局最有解表示到第i天进行j次的收益，这就是该动态规划的特殊之处。

用local[i][j]表示到达第i天时，最多进行j次交易的局部最优解；用global[i][j]表示到达第i天时，最多进行j次的全局最优解。它们二者的关系如下（其中diff = prices[i] – prices[i – 1]）：

    local[i][j] = max(global[i – 1][j – 1] , local[i – 1][j] + diff)
    global[i][j] = max(global[i – 1][j], local[i][j])

local[i][j]和global[i][j]的区别是：local[i][j]意味着在第i天一定有交易（卖出）发生，当第i天的价格高于第i-1天（即diff > 0）时，那么可以把这次交易（第i-1天买入第i天卖出）跟第i-1天的交易（卖出）合并为一次交易，即local[i][j]=local[i-1][j]+diff；当第i天的价格不高于第i-1天（即diff<=0）时，那么local[i][j]=global[i-1][j-1]+diff，而由于diff<=0，所以可写成local[i][j]=global[i-1][j-1]。global[i][j]就是我们所求的前i天最多进行k次交易的最大收益，可分为两种情况：如果第i天没有交易（卖出），那么global[i][j]=global[i-1][j]；如果第i天有交易（卖出），那么global[i][j]=local[i][j]。

代码：
```
 public int maxProfit(int k, int[] prices) {
        if(prices.length < 2)return 0;
        int[][] local = new int[prices.length][k+1];
        int[][] global = new int[prices.length][k+1];
        for(int i=0; i<prices.length; i++){
            local[i][0] = 0;
            global[i][0] = 0;
        }
        for(int i=1; i<prices.length; i++){
            int dif = prices[i] - prices[i-1];
            for(int j=1; j<=k; j++){
                local[i][j] = Math.max(global[i-1][j-1], local[i-1][j]+dif);
                global[i][j] = Math.max(global[i-1][j], local[i][j]);
            }
        }
        return global[prices.length-1][k];
    }
    ```
    memory limit exit...
    代码二：
    ```
    public static int maxProfit(int k, int[] prices){
        if(prices.length < 2)return 0;
        if(prices.length <= k) return maxProfit2(prices);
        int[] local = new int[k+1];
        int[] global = new int[k+1];
        
        for(int i=1; i<prices.length; i++){
            int dif = prices[i] - prices[i-1];
            for(int j=k; j>0; j--){
                local[j] = Math.max(global[j-1], local[j]+dif);
                global[j] = Math.max(global[j], local[j]);
            }
        }
        return global[k];
    }
    public static int maxProfit2(int[] prices){
        int res = 0;
        for(int i=1; i<prices.length; i++){
            int dif = prices[i] - prices[i-1];
            if(dif > 0)res += dif;
        }
        return res;
    }
```


