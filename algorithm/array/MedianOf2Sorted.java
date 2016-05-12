package leet.array;
/**
 * Median of Two Sorted Arrays
 * There are two sorted arrays A and B of size m and n respectively.
 * Find the median of the two sorted arrays.
 * The overall run time complexity should be O(log(m+n))
 */
public class MedianOf2Sorted {
	private static double findMedian(int a[], int b[]){
		int m = a.length + b.length;
		if(m%2 == 1) return (double)findKth(a,0,b,0, m/2+1);
		else return ((double)findKth(a,0,b,0,m/2)+findKth(a,0,b,0,m/2+1))/2;
	}
	//默认l<s
	private static int findKth(int a[], int l, int b[], int s, int k){
		if(a.length-l > b.length-s)return findKth(b,s,a,l,k);
		if(l == a.length)return b[s+k-1];
		if(s == b.length)return a[l+k-1];
		if(k == 1)return a[l]<b[s]?a[l]:b[s];
		int pa = k/2, pb = k - pa;
		if(a[l+pa-1] < b[s+pb-1])return findKth(a,l+pa,b,s,k-pa);
		else if(a[l+pa-1] > b[s+pb-1])return findKth(a,l,b,s+pb,k-pb);
		else return a[l];
	}
	public static void main(String[] args){
		int[] a1 = {1,2,3,4};
		int b1[] = {7,8,9,10};
		System.out.println(MedianOf2Sorted.findMedian(a1, b1));
	}
}
