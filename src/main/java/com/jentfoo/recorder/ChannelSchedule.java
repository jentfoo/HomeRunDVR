package com.jentfoo.recorder;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.threadly.util.Clock;

public class ChannelSchedule {
  public static final Collection<Short> ALL_DAYS;
  private static final short VALID_ALLOWED_VARIATION_IN_MINUTES = 10;
  
  static {
    List<Short> days = new ArrayList<Short>(7);
    for (short i = 0; i < 7; i++) {
      days.add(i);
    }
    
    ALL_DAYS = Collections.unmodifiableCollection(days);
  }
  
  public final short channel;
  public final short hour;
  public final short minute;
  public final short durationInMinutes;
  public final Collection<Short> days;
  
  public ChannelSchedule(short channel, short hour, short minute, short durationInMinutes, 
                         Collection<Short> days) {
    this.channel = channel;
    this.hour = hour;
    this.minute = minute;
    this.durationInMinutes = durationInMinutes;
    this.days = days;
  }

  public boolean timeValid() {
    long currHour = (Clock.lastKnownTimeMillis() / 1000 / 60 / 60) % 24;
    long currMin = (Clock.lastKnownTimeMillis() / 1000 / 60 ) % 60;
    
    long currDayMin = (currHour * 60) + currMin;
    int expectedDayMin = (hour * 60) + minute;
    
    return currDayMin - expectedDayMin <= VALID_ALLOWED_VARIATION_IN_MINUTES;
  }
  
  private short getCurrentDayNum() {
    return (short)(Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1);
  }

  public boolean dayValid() {
    short currDay = getCurrentDayNum();
    
    return days.contains(currDay);
  }
  
  public short daysTillValid() {
    short currDay = getCurrentDayNum();
    for (short d = 0; d < 7; d++) {
      if (days.contains((short)((currDay + d) % 7))) {
        return d;
      }
    }
    
    throw new IllegalStateException("Channel is not valid for any days");
  }
}
