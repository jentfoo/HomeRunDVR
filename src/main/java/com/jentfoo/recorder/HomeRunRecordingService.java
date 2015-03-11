package com.jentfoo.recorder;

import java.io.File;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.threadly.concurrent.AbstractService;
import org.threadly.concurrent.SchedulerServiceInterface;

public class HomeRunRecordingService extends AbstractService {
  private final SchedulerServiceInterface recordScheduler;
  private final List<HttpStreamRecorder> recorders; 
  
  public HomeRunRecordingService(SchedulerServiceInterface masterScheduler, 
                                 SchedulerServiceInterface recordScheduler, 
                                 InetAddress downloadIp, File savePath, 
                                 List<ChannelSchedule> schedule) {
    this.recordScheduler = recordScheduler;
    this.recorders = new ArrayList<HttpStreamRecorder>(schedule.size());
    Iterator<ChannelSchedule> it = schedule.iterator();
    while (it.hasNext()) {
      ChannelSchedule cs = it.next();
      URL requestURL; 
      try {
        requestURL =  new URL("http://" + downloadIp.getHostAddress() + ":5004/auto/v" + cs.channel + "?dlna");
      } catch (MalformedURLException e) {
        throw new RuntimeException(e);
      }
      recorders.add(new HttpStreamRecorder(masterScheduler, requestURL, savePath, cs));
    }
  }

  @Override
  protected void startupService() {
    Iterator<HttpStreamRecorder> it = recorders.iterator();
    while (it.hasNext()) {
      HttpStreamRecorder recorder = it.next();
      short daysTillRecord = recorder.chanSchedule.daysTillValid();
      if (daysTillRecord == 0) {
        System.out.println("Will start recording channel: " + recorder.chanSchedule.channel + 
                             " in " + makeTimeStr(recorder.getDelayInMillisTillStart()) + " minutes");
      } else {
        System.out.println("Scheduling to start recording channel: " + recorder.chanSchedule.channel + 
                             " in " + daysTillRecord + " days");
      }
      
      long period;
      if (recorder.chanSchedule.days.size() == 1) {
        period = TimeUnit.DAYS.toMillis(7);
      } else {
        period = TimeUnit.DAYS.toMillis(1);
      }
      recordScheduler.scheduleAtFixedRate(recorder, recorder.getDelayInMillisTillStart(), period);
    }
  }
  
  private static String makeTimeStr(long millis) {
    int min = (int)((millis / 1000) / 60);
    int seconds = (int)((millis - (min * 1000 * 60)) / 1000);
    
    return min + ":" + StringFormatter.pad(seconds, 2);
  }

  @Override
  protected void shutdownService() {
    Iterator<HttpStreamRecorder> it = recorders.iterator();
    while (it.hasNext()) {
      recordScheduler.remove(it.next());
    }
    
    synchronized (this) {
      this.notifyAll();
    }
  }
  
  public void blockTillShutdown() throws InterruptedException {
    synchronized (this) {
      while (isRunning()) {
        this.wait();
      }
    }
  }
}
