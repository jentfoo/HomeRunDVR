package com.jentfoo.recorder;

import java.io.File;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.threadly.concurrent.SchedulerServiceInterface;
import org.threadly.util.AbstractService;
import org.threadly.util.StringUtils;

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
      long initalDelayMillis = recorder.chanSchedule.getDelayTillStartMillis();
      System.out.println("Scheduling to start recording channel: " + recorder.chanSchedule.channel + 
                           " in " + makeTimeStr(initalDelayMillis));
      
      if (recorder.chanSchedule.days.size() == 1) {
        recordScheduler.scheduleAtFixedRate(recorder, initalDelayMillis, TimeUnit.DAYS.toMillis(7));
      } else {
        recordScheduler.scheduleAtFixedRate(recorder, initalDelayMillis, TimeUnit.DAYS.toMillis(1));
      }
    }
  }
  
  private static String makeTimeStr(long millis) {
    if (millis < TimeUnit.DAYS.toMillis(1)) {
      int min = (int)((millis / 1000) / 60);
      int seconds = (int)((millis - (min * 1000 * 60)) / 1000);
      
      return min + ":" + StringUtils.padStart(Integer.toString(seconds), 2, '0') + " minutes";
    } else {
      long days = TimeUnit.MILLISECONDS.toDays(millis);
      
      return days + " days " + makeTimeStr(millis - TimeUnit.DAYS.toMillis(days));
    }
  }

  @Override
  protected void shutdownService() {
    Iterator<HttpStreamRecorder> it = recorders.iterator();
    while (it.hasNext()) {
      recordScheduler.remove(it.next());
    }
  }
}
