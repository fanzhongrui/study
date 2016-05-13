package leet.stack;

import java.util.Stack;
/**
 * Given a string containing just the characters'('and')',find the length of the longest valid
 * (well-fromed) parentheses substring.
 * For "(()",the longest valid parentheses substring is "()",which has length = 2.
 * Another example is ")()())",where the longest valid parentheses substring is "()()",
 * which has length = 4.
 */
public class LongestValidParentheses {
	private static int longestValid(String s){
		int res = 0, len = 0;
		Stack<Character> stack = new Stack();
		for(int i=0; i<s.length(); i++){
			char c = s.charAt(i);
			switch(c){
			case '(':
				stack.push(c);continue;
			case ')':
				if(!stack.isEmpty() && stack.pop().equals('('))len+=2;
				else {
					res = res < len ? len:res;
					len = 0;
				}
				continue;
			default: 
				res = res < len ? len:res;
				len = 0;
				continue;
			}
		}
		return len>res?len:res;
	}
	public static void main(String[] args){
		String s1 = "((())(((";
		System.out.println(LongestValidParentheses.longestValid(s1));
		String s2 = "(((((";
		System.out.println(LongestValidParentheses.longestValid(s2));
		String s3 = "()()";
		System.out.println(LongestValidParentheses.longestValid(s3));
	}
}
