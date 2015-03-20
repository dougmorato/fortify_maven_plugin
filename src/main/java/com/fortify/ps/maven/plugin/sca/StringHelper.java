package com.fortify.ps.maven.plugin.sca;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


public class StringHelper {
	
	private StringHelper(){}
	
	/**
	 * Convert file separator from Windows style '\' to Unix style '/'
	 * 
	 * @param filePath 
	 * @return file Path which file separator was converted to Unix style 
	*/
	public static String convertFileSeparator( String filePath )
	{
		return filePath.replaceAll("\\\\", "/");
	}
	
	/**
	 * Create Java Regex from Ant and Maven File Pattern
	 * 
	 * @param pattern 
	 * @return Java Regex created from Ant and Maven File Pattern
	*/
	public static String createRegexFromFilePattern(String pattern)
	{
		String regex = "^";
		for(int i = 0; i < pattern.length(); ++i)
		{
			final char c = pattern.charAt(i);
			switch(c)
			{
			case '*':
				regex += ".*";
				break;
			case '?':
				regex += '.';
				break;
			case '.':
				regex += "\\.";
				break;
			case '\\':
				regex += "\\\\";
				break;
			default: regex += c;
			}
		}
		regex += '$';
		return regex;
	}
	
	/**
	 * @param s
	 * @return  True if the input string s is non-null and non-trivial. False otherwise.
	 */
	public static boolean isDefinedNonTrivially(String s) {
		return s != null && s.length() >= 1;
	}

	/**
	 * List items are sequentially put into one string, using the separator to separate items. The separator is not
	 * appended to the end of the string.
	 *
	 * @param list      List of items that should be put into one string.
	 * @param separator The symbol used to delimit items in the resultant string.
	 * @return          A single string containing all list items.
	 */
	public static String listToString(List<String> list, String separator) {
		StringBuffer buf = new StringBuffer();

		if (list != null && list.size() >= 1) {
			int lastElemIndex = list.size() - 1;
			int currIndex = 0;

			Iterator<String> iter = list.iterator();
			while (iter.hasNext()) {
				String listItem = iter.next();
				buf.append(listItem);

				// Append the separator unless it is the last element.
				if (currIndex < lastElemIndex) {
					buf.append(separator);
				}
				currIndex++;
			}
		}
		return buf.toString();
	}

	/**
	 * List items are put together in a string (sequentially) using the system's path separator as the separator.
	 *
	 * @param list
	 * @return A string containing the list items delimited by a path separator.
	 */
	public static String listToString(List<String> list) {
		return listToString(list, File.pathSeparator);
	}

	/**
	 * Combine the two classpaths taking into account either one being null and
	 * adding path separator between the two when necessary.
	 *
	 * @param classPath
	 * @param extraCP
	 * @return A string containing the combined classpath.
	 */
	public static String concatenateClassPaths(String classPath, String extraCP) {
		String cp = null;
		if (classPath != null)
			cp = classPath;
		if (extraCP != null) {
			if (cp != null) {
				cp += File.pathSeparator;
			} else {
				cp = "" ;
			}
			cp += extraCP;
		}
		return cp ;
	}
}
