package leet.array;
/**
 * Search in Rotated Sorted Array
 * Suppose a sorted array is rotated at some pivot unknown to you beforehand.
 * (i.e.0 1 2 4 5 6 7  might become 4 5 6 7 0 1 2)
 * You are given a target value to search. If found in the array return its index,otherwise return -1.
 * You may assume no duplicate exists in the array.
 */
public class searchRotated {
	private static int searchRotatedArray(int[] a, int value){
		int i = 0;
		while(i < a.length){
			if(i>0 && a[i]<a[i-1])break;
			if(a[i] == value)return i;
			i++;
		}
		return binarySearch(a, i, a.length, value);
	}
	private static int binarySearch(int a[], int s, int e, int value){
		while(s < e){
			int m = s + (e-s)/2;
			if(a[m] == value)return m;
			if(a[s]<=value && value<a[m])e = m;
			else s=m+1;
		}
		return -1;
	}
	//满足前后两段均递增
	private static int search(int a[], int value){
		int s=0, e = a.length;
		while(s < e){
			int m = s+(e-s)/2;
			if(a[m]==value)return m;
			if(a[s] < a[m]){
				if(a[s]<=value && value<a[m])e = m;
				else s = m+1;
			}else{
				if(a[m]<value && value<=a[e-1])s = m+1;
				else e = m;
			}
		}
		return -1;
	}
	public static void main(String[] args){
		int a1[] = {4,5,6,7,8,0,1,2,3};
		System.out.print(searchRotated.search(a1, 4)+" ");
		System.out.println(searchRotated.searchRotatedArray(a1, 4));
		
		System.out.print(searchRotated.search(a1, 8)+" ");
		System.out.println(searchRotated.searchRotatedArray(a1, 8));
		
		System.out.print(searchRotated.search(a1, 0)+" ");
		System.out.println(searchRotated.searchRotatedArray(a1, 0));
		
		System.out.print(searchRotated.search(a1, 3)+" ");
		System.out.println(searchRotated.searchRotatedArray(a1, 3));
		
		int a2[] = {0,1,2,3,4,5,6,7,8,9};
		System.out.print(searchRotated.search(a2, 0)+" ");
		System.out.println(searchRotated.searchRotatedArray(a2, 0));
		
		System.out.print(searchRotated.search(a2, 9)+" ");
		System.out.println(searchRotated.searchRotatedArray(a2, 9));
		
		int a3[] = {10,9,8,7,6,5,4,3,2,1};
		System.out.print(searchRotated.search(a3, 10)+" ");
		System.out.println(searchRotated.searchRotatedArray(a3, 10));
		
//		System.out.print(searchRotated.search(a3, 1)+" ");
//		System.out.println(searchRotated.searchRotatedArray(a3, 1));
		
	}
}
