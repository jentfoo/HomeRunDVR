package com.jentfoo.recorder;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;
import java.net.URL;
import java.util.Calendar;

import org.threadly.concurrent.SchedulingUtils;
import org.threadly.concurrent.SimpleSchedulerInterface;

import com.github.kevinsawicki.http.HttpRequest;

public class HttpStreamRecorder implements Runnable {
  private static final int READ_TIMEOUT = 1000;
  private static final int CONNECT_TIMEOUT = 1000;
  private static final int BUFFER_SIZE = 4096;
  
  private final SimpleSchedulerInterface scheduler;
  private final URL requestURL;
  private final File savePath;
  public final ChannelSchedule chanSchedule;
  
  public HttpStreamRecorder(SimpleSchedulerInterface scheduler, 
                            URL requestURL, File savePath, ChannelSchedule schedule) {
    this.scheduler = scheduler;
    this.requestURL = requestURL;
    this.savePath = savePath;
    this.chanSchedule = schedule;
  }
  
  public long getDelayInMillisTillStart() {
    return SchedulingUtils.getDelayTillHour(chanSchedule.hour, chanSchedule.minute);
  }
  
  private File getDownloadFile() {
    Calendar cal = Calendar.getInstance();
    
    String name = Short.toString(chanSchedule.channel) + "-" + 
                    cal.get(Calendar.YEAR) + StringFormatter.pad(cal.get(Calendar.MONTH), 2) + StringFormatter.pad(cal.get(Calendar.DAY_OF_MONTH), 2) + "-" + 
                    ((chanSchedule.hour * 60) + chanSchedule.minute) + ".mp4";
    
    File result = new File(savePath, name);
    
    try {
      if (! result.createNewFile()) {
        throw new IllegalStateException("Failed to create file: " + result);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    
    return result;
  }
  
  public HttpRequest makeRequest() {
    final HttpRequest request = HttpRequest.get(requestURL);
    request.readTimeout(READ_TIMEOUT);
    request.connectTimeout(CONNECT_TIMEOUT);
    scheduler.schedule(new Runnable() {
      @Override
      public void run() {
        System.out.println("Stopping recording of channel: " + chanSchedule.channel);
        
        request.disconnect();
      }
    }, chanSchedule.durationInMinutes * 1000 * 60);
    
    return request;
  }

  @Override
  public void run() {
    if (! chanSchedule.timeValid()) {
      System.err.println("Time for channel: " + chanSchedule.channel + 
                           " is not close enough to start recording, this could indicate too many overlapping schedules");
      
      return;
    } else if (! chanSchedule.dayValid()) {
      // don't record on days we are not interested in
      return;
    } else {
      System.out.println("Starting download of channel: " + chanSchedule.channel + 
                           " for " + chanSchedule.durationInMinutes + " minutes");
    }
    
    File downloadFile = getDownloadFile();
    HttpRequest request = makeRequest();
    InputStream requestStream = request.stream();
    try {
      OutputStream fileOut = null;
      try {
        fileOut = new BufferedOutputStream(new FileOutputStream(downloadFile));
        byte[] buffer = new byte[BUFFER_SIZE];
        int readCount;
        while ((readCount = requestStream.read(buffer)) > -1) {
          fileOut.write(buffer, 0, readCount);
        }
      } finally {
        if (fileOut != null) {
          fileOut.close();
        }
      }
    } catch (SocketException e) {
      // expected once disconnected
    } catch (IOException e) {
      throw new RuntimeException(e);
    } finally {
      try {
        requestStream.close();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
}