package com.jentfoo.recorder;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.threadly.concurrent.SchedulingUtils;

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
                         Collection<Short> days, boolean timeIsUTC) {
    this.channel = channel;
    if (timeIsUTC) {
      this.hour = hour;
    } else {
      this.hour = (short)SchedulingUtils.shiftLocalHourToUTC(hour);
    }
    this.minute = minute;
    this.durationInMinutes = durationInMinutes;
    this.days = days;
  }
  
  public long getDelayTillStartMillis() {
    return TimeUnit.DAYS.toMillis(daysTillValid()) + SchedulingUtils.getDelayTillHour(hour, minute);
  }

  public boolean timeValid() {
    long currDayMin = (TimeUtils.getCurrHour() * 60) + TimeUtils.getCurrMin();
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
  
  /**
   * Call to check how many days till the schedule is valid.  This will only be > 0 if there is 
   * more than 24 hours till the recording should start.  So this may return zero, but 
   * {@link #dayValid()} may return false since the recording should not start till tomorrow.
   * 
   * @return how many days till the schedule should run
   */
  public short daysTillValid() {
    short currDay = getCurrentDayNum();
    for (short i = 0; i < 7; i++) {
      short d = (short)((currDay + i) % 7);
      if (days.contains(d)) {
        if (i == 1) {
          long currHour = TimeUtils.getCurrHour();
          if (currHour > hour || (currHour == hour && TimeUtils.getCurrMin() > minute)) {
            // we have passed the time, so it will be less than 1 day
            return 0;
          } else {
            return i;
          }
        } else {
          return i;
        }
      }
    }
    
    throw new IllegalStateException("Channel is not valid for any days");
  }
}
