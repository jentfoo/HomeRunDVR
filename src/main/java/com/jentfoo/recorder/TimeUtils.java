package com.jentfoo.recorder;

import org.threadly.util.Clock;

public class TimeUtils {
  public static long getCurrHour() {
    return getHour(Clock.lastKnownTimeMillis());
  }
  
  public static long getHour(long nowMillis) {
    return (nowMillis / 1000 / 60 / 60) % 24;
  }
  
  public static long getCurrMin() {
    return getMin(Clock.lastKnownTimeMillis());
  }
  
  public static long getMin(long nowMillis) {
    return (nowMillis / 1000 / 60 ) % 60;
  }
}
