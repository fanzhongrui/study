package leet.array;
/**
 * Search in Rotated Sorted Array II
 * follow up for "Search in Rotated Sorted Array":what if duplicates are allowed?
 * would this affect the run-time complexity?How and why?
 * write a function to determine if a given target is in the array.
 */
public class searchRotated2 {
	private static int searchRotatedArray(int a[], int value){
		int i=0;
		while(i<a.length){
			if(i>0 && a[i]<a[i-1])break;
			if(a[i] == value) return i;
			i++;
		}
		return binarySearch(a, i, a.length, value);
	}
	private static int binarySearch(int[] a, int s, int e, int value){
		while(s<e){
			int m = s+(e-s)/2;
			if(a[m] == value)return m;
			if(a[s]<=value && value<a[m])e = m;
			else s=m+1;
		}
		return -1;
	}
	private static int search2(int[] a, int value){
		int s = 0, e = a.length;
		while(s < e){
			int m = s + (e - s)/2;
			if(a[m] == value)return m;
			if(a[s] < a[m]){
				if(a[s] <= value && value < a[m])e = m;
				else s = m+1;
			}else if(a[s] > a[m]){
				if(a[m] < value && value <= a[e-1])s = m+1;
				else e = m;
			}else s++;
		}
		return -1;
	}
	public static void main(String[] args){
		int[] a1={1,1,1,2,2,3,1,1,1};
		System.out.print(searchRotated2.search2(a1, 1)+" ");
		System.out.println(searchRotated2.searchRotatedArray(a1, 1));
		
		System.out.print(searchRotated2.search2(a1, 2)+" ");
		System.out.println(searchRotated2.searchRotatedArray(a1, 2));
		
		System.out.print(searchRotated2.search2(a1, 3)+" ");
		System.out.println(searchRotated2.searchRotatedArray(a1, 3));
		
		int[] a2={1,1,1,2,1,1,1};
		System.out.print(searchRotated2.search2(a2, 1)+" ");
		System.out.println(searchRotated2.searchRotatedArray(a2, 1));
		
		System.out.print(searchRotated2.search2(a2, 2)+" ");
		System.out.println(searchRotated2.searchRotatedArray(a2, 2));
	}
}
