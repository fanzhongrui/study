package leet.array;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
/**
 * Given an array S of n integers,are there elements a,b,c and d in S such that a+b+c+d=target?
 * Find all unique quadruplets in the array which gives the sum of target.
 * Note:
 * Elements in a quadruplets (a,b,c,d)must be in non-descending order.(i.e,a<=b<=c<=d)
 * The solution set must not contain duplicate quadruplets.
 * For example,given array S={1 0 -1 0 -2 2},and target = 0.
 * A solution set is :(-1, 0, 0, 1)(-2, -1, 1, 2)(-2, 0, 0, 2)
 */
public class FourSum {
	private static List<List<Integer>> fourSum(int a[], int target){
		List<List<Integer>> res = new ArrayList();
		Arrays.sort(a);
		for(int i=0; i<a.length; i++){
			for(int j=i+1; j<a.length; j++){
				int k=j+1, t = a.length-1;
				while(k<t){
					int sum = a[i]+a[j]+a[k]+a[t];
					if(sum<target)k++;
					else if(sum > target)t--;
					else {
						List<Integer> list = new ArrayList();
						list.add(a[i]);
						list.add(a[j]);
						list.add(a[k]);
						list.add(a[t]);
						if(!res.contains(list))res.add(list);
						k++;
						t--;
					}
				}
			}
		}
		return res;
	}
	private static void print(List<List<Integer>> res){
		Iterator<List<Integer>> it = res.iterator();
		while(it.hasNext()){
			List<Integer> list = it.next();
			System.out.println(list.get(0)+" "+list.get(1)+" "+list.get(2)+" "+list.get(3));
		}
	}
	public  static void main(String[] args){
		int a[] = {0,1,2,3,4,0,1,2,15};
		FourSum.print(FourSum.fourSum(a, 15));
		System.out.println();
		FourSum.print(FourSum.fourSum(a, 16));
		System.out.println();
		FourSum.print(FourSum.fourSum(a, 17));
	}
}
