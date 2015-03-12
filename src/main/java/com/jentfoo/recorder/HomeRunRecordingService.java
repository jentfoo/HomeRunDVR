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
  public final SchedulerServiceInterface recordScheduler;
  public final File savePath;
  private final InetAddress downloadIp;
  private final List<HttpStreamRecorder> recorders; 
  
  public HomeRunRecordingService(SchedulerServiceInterface masterScheduler, 
                                 SchedulerServiceInterface recordScheduler, 
                                 InetAddress downloadIp, File savePath, 
                                 List<ChannelSchedule> schedule) {
    this.recordScheduler = recordScheduler;
    this.savePath = savePath;
    this.downloadIp = downloadIp;
    this.recorders = new ArrayList<HttpStreamRecorder>(schedule.size());
    Iterator<ChannelSchedule> it = schedule.iterator();
    while (it.hasNext()) {
      ChannelSchedule cs = it.next();
      URL requestURL; 
      try {
        requestURL = makeRequestURL(cs.channel);
      } catch (MalformedURLException e) {
        throw new RuntimeException(e);
      }
      recorders.add(new HttpStreamRecorder(masterScheduler, requestURL, savePath, cs));
    }
  }
  
  public URL makeRequestURL(short channel) throws MalformedURLException {
    return new URL("http://" + downloadIp.getHostAddress() + ":5004/auto/v" + channel + "?dlna");
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
      
      if (recorder.chanSchedule.days.size() == 1) {
        long timeDelay = recorder.getDelayInMillisTillStart();
        long dayPointInMillis = TimeUnit.HOURS.toMillis(recorder.chanSchedule.hour) + 
                                  TimeUnit.MINUTES.toMillis(recorder.chanSchedule.minute);
        long initialDelay;
        if (timeDelay > dayPointInMillis) {
          initialDelay = TimeUnit.DAYS.toMillis(daysTillRecord) + timeDelay;
        } else {
          initialDelay = TimeUnit.DAYS.toMillis(daysTillRecord - 1) + timeDelay;
        }
        recordScheduler.scheduleAtFixedRate(recorder, initialDelay, TimeUnit.DAYS.toMillis(7));
      } else {
        recordScheduler.scheduleAtFixedRate(recorder, recorder.getDelayInMillisTillStart(), TimeUnit.DAYS.toMillis(1));
      }
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
  }
}
