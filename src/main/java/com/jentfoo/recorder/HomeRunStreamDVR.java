package com.jentfoo.recorder;

import java.io.File;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;

import org.threadly.concurrent.PriorityScheduler;
import org.threadly.concurrent.limiter.SchedulerServiceLimiter;
import org.threadly.util.ExceptionHandlerInterface;
import org.threadly.util.ExceptionUtils;

public class HomeRunStreamDVR {
  private static final PriorityScheduler scheduler = new PriorityScheduler(8, 16, 1000 * 60);
  
  public static void main(String[] args) throws InterruptedException {
    ExceptionHandler eh = new ExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler(eh);
    ExceptionUtils.setDefaultExceptionHandler(eh);
    
    HomeRunRecordingService service = null;
    try {
      service = parseAndMakeService(args);
    } catch (Exception e) {
      System.err.println(e.getMessage());
      usageAndExit();
    }
    service.start();
    
    service.blockTillShutdown();
    scheduler.shutdown();
  }
  
  private static HomeRunRecordingService parseAndMakeService(String[] args) throws UnknownHostException {
    if (args.length < 4) {
      throw new IllegalArgumentException("Must supply at least 4 arguments");
    }
    
    InetAddress downloadIp = InetAddress.getByName(args[0]);
    
    String savePathStr = args[1];
    File savePath = new File(savePathStr);
    if (! savePath.exists() && ! savePath.mkdirs()) {
      throw new IllegalArgumentException("Can not create save path: " + savePathStr);
    }
    
    short maxInParallel = Short.parseShort(args[2]);
    if (maxInParallel < 1) {
      throw new IllegalArgumentException("Must allow at least 1 in parallel stream");
    }
    
    List<ChannelSchedule> schedule = new ArrayList<ChannelSchedule>(args.length - 2);
    for (int i = 3; i < args.length; i++) {
      int chanDelimIndex = args[i].indexOf(',');
      if (chanDelimIndex < 0) {
        throw new IllegalArgumentException("Could not parse channel from: " + args[i]);
      }
      int timeDelimIndex = args[i].indexOf(':', chanDelimIndex);
      if (timeDelimIndex < 0) {
        throw new IllegalArgumentException("Could not parse time from: " + args[i]);
      }
      int durationDelimIndex = args[i].indexOf(',', timeDelimIndex);
      if (durationDelimIndex < 0) {
        throw new IllegalArgumentException("Could not parse duration from: " + args[i]);
      }
      
      Collection<Short> recordDays;
      int dayDelimIndex = args[i].indexOf(',', durationDelimIndex + 1);
      if (dayDelimIndex < 0) {
        recordDays = ChannelSchedule.ALL_DAYS;
      } else {
        String days = args[i].substring(dayDelimIndex + 1);
        if (days.length() != 7) {
          throw new IllegalArgumentException("Must supply 0 or 1 for every day to indicate record or not");
        }
        recordDays = new ArrayList<Short>(1);
        for (short d = 0; d < 7; d++) {
          short val = Short.parseShort("0" + days.charAt(d));
          if (val != 0 && val != 1) {
            throw new IllegalArgumentException("Invalid enable definition for day: " + d + ", value: " + val);
          }
          if (val == 1) {
            recordDays.add(d);
          }
        }
        
        if (recordDays.isEmpty()) {
          throw new IllegalArgumentException("Channel not enabled for any days");
        }
      }

      Calendar calendar = Calendar.getInstance();
      int hourShift = (calendar.get(Calendar.ZONE_OFFSET) + calendar.get(Calendar.DST_OFFSET)) / 1000 / 60 / 60;
      try {
        short channel = Short.parseShort(args[i].substring(0, chanDelimIndex));
        short hour = Short.parseShort(args[i].substring(chanDelimIndex + 1, timeDelimIndex));
        short minute = Short.parseShort(args[i].substring(timeDelimIndex + 1, durationDelimIndex));
        short duration;
        if (dayDelimIndex < 0) {
          duration = Short.parseShort(args[i].substring(durationDelimIndex + 1));
        } else {
          duration = Short.parseShort(args[i].substring(durationDelimIndex + 1, dayDelimIndex));
        }
        
        hour -= hourShift;
        if (hour > 23) {
          hour %= 24;
        } else if (hour < 0) {
          hour += 24;
        }
        
        schedule.add(new ChannelSchedule(channel, hour, minute, duration, recordDays));
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Illegal character in parsing channel, time, or duration", e);
      }
    }
    
    return new HomeRunRecordingService(scheduler, new SchedulerServiceLimiter(scheduler, maxInParallel), 
                                downloadIp, savePath, schedule);
  }
  
  private static void usageAndExit() {
    System.err.println("Usage: java " + HomeRunStreamDVR.class.getName() + 
                         " [HomeRunIP] [savePath] [maxInParallelStreams] [channel,hours:min,durationInMinutes(,00000000)]...");
    System.exit(1);
  }
  
  private static class ExceptionHandler implements ExceptionHandlerInterface, UncaughtExceptionHandler {
    @Override
    public void uncaughtException(Thread t, Throwable e) {
      System.err.println("Thread: " + t + "...threw exception: ");
      e.printStackTrace();
      System.exit(2);
    }

    @Override
    public void handleException(Throwable thrown) {
      System.err.println("Uncaught exception: ");
      thrown.printStackTrace();
      System.exit(2);
    }
  }
}
