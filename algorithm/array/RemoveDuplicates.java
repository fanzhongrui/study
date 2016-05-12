package leet.array;
/**
 * Given a sorted array,remove the duplicates in place such that 
 * each element appear only once and return the new length.
 * Do not allocate extra space for another array,you must do this in place with constant memory.
 * For example, Given input array A=[1,1,2],
 * Your function should return length=2,and A is now [1,2]
 */
public class RemoveDuplicates {
	public int function(int[] a){
		int i = 1, j = 1;
		if(a.length > 1){
			while(j < a.length){
				if(a[j] == a[j-1]){
					j++;
					continue;
				}
				a[i++] = a[j++];
			}
		}
		return i;
	}
	public static void main(String[] args){
		RemoveDuplicates rd = new RemoveDuplicates();
		int[] a1 = {0,0,0,0,0};
		System.out.println(rd.function(a1));
		int[] a2 = {0,0,0,0,1};
		System.out.println(rd.function(a2));
		int[] a3 = {0,1,2,3,4};
		System.out.println(rd.function(a3));
		int[] a4 = {0,1,1,1,2};
		System.out.println(rd.function(a4));
	}
}
