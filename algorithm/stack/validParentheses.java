package leet.stack;

import java.util.Stack;
/**
 * Given a string containing just the characters '(',')','{','}','[',']',
 * determine if the input string is valid.
 * The brackets must close in the correct order."()"and "()[]"are all valid
 * but "(]" and "([)]"are not.
 */
public class validParentheses {
	private static boolean isValid(String s){
		Stack<Character> stack = new Stack();
		for(int i=0; i<s.length(); i++){
			char c = s.charAt(i);
			switch(c){
			case '(': stack.push(c);continue;
			case '{': stack.push(c);continue;
			case '[': stack.push(c);continue;
			case ')': 
					if(stack.pop().equals('(')) continue ;
					else return false;
			case '}': 
					if(stack.pop().equals('{')) continue ;
					else return false;
			case ']': 
					if(stack.pop().equals('[')) continue ;
					else return false;
			default:return false;
			}
		}
		return stack.isEmpty();
	}
	public static void main(String[] args){
		String s1 = "((({{{[]}}})))";
		System.out.println(validParentheses.isValid(s1));
		String s2 = "(((){}{}))";
		System.out.println(validParentheses.isValid(s2));
		String s3 = "((({{[]}))})";
		System.out.println(validParentheses.isValid(s3));
	}
}
