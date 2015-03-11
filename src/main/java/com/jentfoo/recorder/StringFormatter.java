package com.jentfoo.recorder;

public class StringFormatter {
  public static String pad(int val, int charCount) {
    String valStr = Integer.toString(val);
    int padCount = charCount - valStr.length();
    if (padCount <= 0) {
      return valStr;
    } else {
      StringBuilder resultBuilder = new StringBuilder(charCount);
      for (int i = 0; i < padCount; i++) {
        resultBuilder.append('0');
      }
      resultBuilder.append(valStr);
      
      return resultBuilder.toString();
    }
  }
}
