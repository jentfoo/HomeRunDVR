package com.jentfoo.recorder;

import org.threadly.util.Clock;

public class TimeUtils {
  public static long getCurrHour() {
    return (Clock.lastKnownTimeMillis() / 1000 / 60 / 60) % 24;
  }
  
  public static long getCurrMin() {
    return (Clock.lastKnownTimeMillis() / 1000 / 60 ) % 60;
  }
}
