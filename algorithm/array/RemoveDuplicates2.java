package leet.array;
/**
 * Follow up for "Remove Duplicates":what if duplicates are allowed at most twice?
 * for example, given sorted array A = [1,1,1,2,2,3],
 * Your function should return length = 5, and A is now [1,1,2,2,3]
 */
public class RemoveDuplicates2 {
	public int function(int[] a){
		int i = 1, j = 1, cnt = 1;
		if(a.length > 1){
			while(j < a.length){
				if(a[j] == a[j-1])cnt++;
				else cnt = 1;
				if(cnt < 2)a[i++] = a[j++];
			}
		}
		return i;
	}
	public static void main(String[] args){
		RemoveDuplicates2 rd = new RemoveDuplicates2();
		int[] a1 = {0,0,0,0,0};
		System.out.println(rd.function(a1));
		int[] a2 = {0,0,0,0,1,1,1};
		System.out.println(rd.function(a2));
		int[] a3 = {0,1,1,3,4,4};
		System.out.println(rd.function(a3));
		int[] a4 = {0,1,1,1,2,3};
		System.out.println(rd.function(a4));
	}
}
