package leet.array;

import java.util.HashMap;
/**
 * Given an unsorted array of integers, find the length of the longest consecutive elements sequence.
 * For example, given [100, 4, 200, 1,3,2],the longest consecutive elements sequences is[1,2,3,4].
 * Return its length:4
 * Your algorithm should run in O(n) complexity.
 */
public class LongestConsecutive {
	private static int longest(int a[]){
		int length = 0;
		HashMap<Integer, Boolean> m = new HashMap();
		for(int x : a) m.put(x,false);
		for(int x : a){
			if(m.get(x))continue;
			int len = 0, t = x;
			while(m.containsKey(t)){
				len++;
				m.put(t++, true);
			}
			t = x-1;
			while(m.containsKey(t)){
				len++;
				m.put(t--,true);
			}
			length = length > len ? length:len;
		}
		return length;
	}
	public static void main(String[] args){
		int[] a1 = {0,9,5,3,1};
		System.out.println(LongestConsecutive.longest(a1));
		int[] a2 = {0,0,0,0,0};
		System.out.println(LongestConsecutive.longest(a2));
	}
}
