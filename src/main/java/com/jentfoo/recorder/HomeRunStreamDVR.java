package com.jentfoo.recorder;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.threadly.concurrent.PriorityScheduler;
import org.threadly.concurrent.SchedulingUtils;
import org.threadly.concurrent.limiter.SchedulerServiceLimiter;
import org.threadly.util.ExceptionHandlerInterface;
import org.threadly.util.ExceptionUtils;

public class HomeRunStreamDVR {
  private static final PriorityScheduler scheduler = new PriorityScheduler(8, 16, 1000 * 60);
  private static final int hourShift;
  
  static {
    Calendar calendar = Calendar.getInstance();
    hourShift = (calendar.get(Calendar.ZONE_OFFSET) + calendar.get(Calendar.DST_OFFSET)) / 1000 / 60 / 60;
  }
  
  public static void main(String[] args) throws InterruptedException, IOException {
    ExceptionHandler eh = new ExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler(eh);
    ExceptionUtils.setDefaultExceptionHandler(eh);
    
    HomeRunRecordingService service = null;
    try {
      service = parseAndMakeService(args);
    } catch (Exception e) {
      System.err.println(e.getMessage());
      System.err.println();
      usageAndExit();
    }
    service.start();
    
    try {
      BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
      String line;
      System.out.println("\nReady to accept commands for on demand recording...");
      System.out.println("To start a recording type in the format: channel,durationInMinutes");
      System.out.println("You can also specify an absolute end time in this format: channel,hour:minute");
      while ((line = reader.readLine()) != null) {
        if (line.equalsIgnoreCase("exit")) {
          System.out.println("Exiting...");
          return;
        }
        int chanDelimIndex = line.indexOf(',');
        if (chanDelimIndex < 0) {
          System.err.println("Could not parse request: '" + line + "'");
          continue;
        }
        
        try {
          short channel = Short.parseShort(line.substring(0, chanDelimIndex));
          short duration;
          int timeDelimIndex = line.indexOf(':', chanDelimIndex);
          if (timeDelimIndex < 0) {
            duration = Short.parseShort(line.substring(chanDelimIndex + 1));
          } else {
            short hour = Short.parseShort(line.substring(chanDelimIndex + 1, timeDelimIndex));
            short min = Short.parseShort(line.substring(timeDelimIndex + 1));
            duration = (short)TimeUnit.MILLISECONDS.toMinutes(SchedulingUtils.getDelayTillHour(shiftHour(hour), min));
          }
          
          Calendar cal = Calendar.getInstance();
          ChannelSchedule schedule = new ChannelSchedule(channel, shiftHour((short)cal.get(Calendar.HOUR_OF_DAY)), (short)cal.get(Calendar.MINUTE), duration, 
                                                         ChannelSchedule.ALL_DAYS);
          
          service.recordScheduler.execute(new HttpStreamRecorder(scheduler, service.makeRequestURL(schedule.channel), 
                                                                 service.savePath, schedule));
        } catch (NumberFormatException e) {
          System.err.println("Could not channel or duration from line: '" + line + "'");
          System.err.println("Format is: channel,durationInMinutes");
        }
      }
    } finally {
      scheduler.shutdown();
    }
  }
  
  private static short shiftHour(short hour) {
    hour -= hourShift;
    if (hour > 23) {
      hour %= 24;
    } else if (hour < 0) {
      hour += 24;
    }
    return hour;
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
        
        hour = shiftHour(hour);
        
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
    System.err.println();
    System.err.println("Arguments:");
    System.err.println("  HomeRunIP - The IP address of the HD HomeRun");
    System.err.println("  savePath - Path to directory to save mp4 files from HD HomeRun streams");
    System.err.println("  maxInParallelStreams - Maximum number of streams that can be recorded in parallel...");
    System.err.println("  channel,hours:min,durationInMinutes(,00000000) - Defines a channel and a schedule");
    System.err.println("    channel - Numeric channel to be recorded");
    System.err.println("    hours:min - The time (in a 24 hour clock format) which the recording should start");
    System.err.println("    durationInMinutes - How many minutes the stream should be recorded for");
    System.err.println("    00000000 - Optional argument to specify which week days the schedule is valid for");
    System.err.println("      If the argument is not included, the schedule will run every day.");
    System.err.println("      If you want to control what day it runs on, you must specify a 0 or 1 for EACH day.");
    System.err.println("      1 indicates that it should record on that day, 0 indicates it should NOT record.");
    System.err.println("      The location for each 0/1 coorosponds to: Sun Mon Tues Wed Thurs Fri Sat.");
    System.err.println("      For example if you only want to record on the weekends you would supply: 1000001.");
    System.err.println("      Alternatively if you wanted to record only on Thursday it would be: 0000100.");
    System.err.println("    A complete schedule definition example which would record, ");
    System.err.println("    Channel 10 every week day at 4 to 5pm AND channel 20 every day at 10 to 10:30am:");
    System.err.println("    10,16:00,60,0111110 20,10:00,30");
    
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
