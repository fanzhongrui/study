package leet.array;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
/**
 * Given an array S of n integers, are there elements a, b,b c in S such that a+b+c=0?
 * Find all unique triplets in the array which gives  the sum of zero.
 * Note : 
 * Elements in a triplet(a,b,c) must be in non-descending order(ie.a<=b<=c)
 * The solution set must not contain duplicate triplets.
 * For example, given array S={-1 0 1 2 -1 -4}.
 * A solution set is:(-1, 0, 1)(-1, -1, 2)
 */
public class ThreeSum {
	private static List<List<Integer> > threeSum(int a[]){
		List<List<Integer>> res = new ArrayList();
		Arrays.sort(a);
		for(int i=0; i<a.length; i++){
			int x = a[i]*-1;
			int j=i+1, k=a.length-1;
			while(j<k){
				int sum = a[j]+a[k];
				if(sum < x)j++;
				else if(sum > x)k--;
				else {
					List<Integer> list = new ArrayList<Integer>();
					list.add(a[i]);
					list.add(a[j]);
					list.add(a[k]);
					if(!res.contains(list))res.add(list);
					j++;k--;
				}
			}
		}
		return res;
	}
	private static void print(List<List<Integer>> res){
		Iterator<List<Integer>> it = res.iterator();
		while(it.hasNext()){
			List<Integer> list = it.next();
			System.out.println(list.get(0)+" "+list.get(1)+" "+list.get(2));
		}
	}
	public static void main(String[] args){
		int[] a1 = {0,-1,1,2,3,-5,4,6};
		ThreeSum.print(ThreeSum.threeSum(a1));
		System.out.println();
		int[] a2 = {0,0,0,0,-1,2,1,-2,-1,1};
		ThreeSum.print(ThreeSum.threeSum(a2));
	}
}
