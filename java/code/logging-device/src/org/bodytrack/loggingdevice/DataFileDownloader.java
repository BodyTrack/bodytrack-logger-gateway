package org.bodytrack.loggingdevice;

import java.io.IOException;
import java.util.SortedSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import edu.cmu.ri.createlab.util.thread.DaemonThreadFactory;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * <p>
 * <code>DataFileDownloader</code> downloads {@link DataFile}s from a {@link LoggingDevice} and saves them to local
 * storage.
 * </p>
 *
 * @author Chris Bartley (bartley@cmu.edu)
 */
public final class DataFileDownloader extends BaseDataFileTransporter
   {
   private static final Logger LOG = Logger.getLogger(DataFileDownloader.class);

   private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(new DaemonThreadFactory(DataFileDownloader.class + ".executor"));
   private final Runnable downloadFileCommand = new DownloadFileCommand();

   /**
    * Constructs a <code>DataFileDownloader</code> for the given {@link LoggingDevice}.
    *
    * @throws IllegalStateException if a {@link DataFileManager} cannot be created for the given {@link LoggingDevice}
    * or if the {@link LoggingDeviceConfig} returned by the given {@link LoggingDevice} is <code>null</code>.
    *
    * @see DataFileManager
    */
   public DataFileDownloader(@NotNull final LoggingDevice device) throws IllegalStateException
      {
      super(device);
      }

   /**
    * Starts the <code>DataFileDownloader</code>, causing it to start downloading {@link DataFile}s from the
    * {@link LoggingDevice} given to the constructor.
    *
    * @throws IllegalStateException if {@link @shutdown} has already been called.
    */
   public void startup() throws IllegalStateException
      {
      LOG.debug("DataFileDownloader.startup()");
      super.startup();
      }

   @Override
   protected void performUponStartup()
      {
      // schedule the download file command, which will reschedule itself upon completion
      scheduleNextFileDownload(0, TimeUnit.SECONDS);
      }

   /**
    * Shuts down the <code>DataFileDownloader</code>, causing it to stop downloading {@link DataFile}s. Once it is shut
    * down, it cannot be started up again.
    */
   public void shutdown()
      {
      LOG.debug("DataFileDownloader.shutdown()");
      super.shutdown();
      }

   @NotNull
   @Override
   protected ExecutorService getExecutor()
      {
      return executor;
      }

   private void scheduleNextFileDownload(final int delay, final TimeUnit timeUnit)
      {
      executor.schedule(downloadFileCommand, delay, timeUnit);
      }

   private final class DownloadFileCommand implements Runnable
      {
      @Override
      public void run()
         {
         LOG.debug("DataFileDownloader$DownloadFileCommand.run(): Calling LoggingDevice.getAvailableFilenames()...");

         int delayUntilNextDownload = 5;
         TimeUnit timeUnit = TimeUnit.SECONDS;

         // get the Set of available filenames from the device.  We'll iterate over each one and take appropriate action
         final SortedSet<String> availableFilenames = getDevice().getAvailableFilenames();

         if (availableFilenames == null)
            {
            // the command failed, so try again in a bit
            LOG.debug("DataFileDownloader$DownloadFileCommand.run(): Failed to get a filename.");
            delayUntilNextDownload = 1;
            timeUnit = TimeUnit.MINUTES;
            }
         else if (availableFilenames.isEmpty())
            {
            // no files available
            LOG.debug("DataFileDownloader$DownloadFileCommand.run(): No data available.");
            delayUntilNextDownload = 5;
            timeUnit = TimeUnit.MINUTES;
            }
         else
            {
            if (LOG.isDebugEnabled())
               {
               LOG.debug("DataFileDownloader$DownloadFileCommand.run(): Device has [" + availableFilenames.size() + "] available file(s)");
               }

            // we got some filenames, so process them...
            for (final String filename : availableFilenames)
               {
               if (LOG.isDebugEnabled())
                  {
                  LOG.debug("DataFileDownloader$DownloadFileCommand.run(): Procesing file [" + filename + "]");
                  }

               // there's a file available on the device, so now check whether it's one we already have
               final DataFileManager.DataFileStatus fileStatus = getDataFileManager().getDataFileStatus(filename);

               if (fileStatus == null)
                  {
                  // the file doesn't already exist, so try to get it from the device
                  try
                     {
                     final DataFile dataFile = getDevice().getFile(filename);

                     if (dataFile == null)
                        {
                        // the command failed, so try again in a bit
                        LOG.debug("DataFileDownloader$DownloadFileCommand.run(): File download failed.");
                        delayUntilNextDownload = 1;
                        timeUnit = TimeUnit.MINUTES;
                        }
                     else if (dataFile.isEmpty())
                        {
                        // there's no data available, so try again in after a while
                        LOG.debug("DataFileDownloader$DownloadFileCommand.run(): No data available.");
                        delayUntilNextDownload = 5;
                        timeUnit = TimeUnit.MINUTES;
                        }
                     else
                        {
                        // TODO: check the checksum

                        // success, so ask the DataFileManager to save the file
                        try
                           {
                           getDataFileManager().save(dataFile);
                           LOG.debug("DataFileDownloader$DownloadFileCommand.run(): File download succeeded!");
                           }
                        catch (IOException e)
                           {
                           LOG.error("DataFileDownloader$DownloadFileCommand.run(): IOException while trying to save the DataFile [" + dataFile + "]", e);
                           }
                        }
                     }
                  catch (NoSuchFileException ignored)
                     {
                     LOG.error("DataFileDownloader$DownloadFileCommand.run(): NoSuchFileException while trying to download file [" + filename + "] from the device.  Ignoring.");
                     }
                  }
               else
                  {
                  // the file already exists, so branch according to the file status
                  switch (fileStatus)
                     {
                     case DOWNLOADED:
                        // if the file has already been downloaded, then don't do anything
                        if (LOG.isDebugEnabled())
                           {
                           LOG.debug("DataFileDownloader$DownloadFileCommand.run(): file [" + filename + "] has already been downloaded, so there's no need to download it again the device");
                           }
                        break;

                     case UPLOADING:
                        // if the file is currently uploading, then don't do anything
                        if (LOG.isDebugEnabled())
                           {
                           LOG.debug("DataFileDownloader$DownloadFileCommand.run(): file [" + filename + "] is currently being uploaded, so there's no need to download it from the device");
                           }
                        break;

                     case UPLOADED:
                        // if the file has already been uploaded, then we can safely delete the file from the device
                        if (LOG.isDebugEnabled())
                           {
                           LOG.debug("DataFileDownloader$DownloadFileCommand.run(): file has already been uploaded--now asking device to delete file [" + filename + "]");
                           }
                        if (getDevice().eraseFile(filename))
                           {
                           if (LOG.isDebugEnabled())
                              {
                              LOG.debug("DataFileDownloader$DownloadFileCommand.run(): file [" + filename + "] successfully erased from device.");
                              }
                           }
                        else
                           {
                           LOG.error("DataFileDownloader$DownloadFileCommand.run(): failed to erase file [" + filename + "] from device.");
                           }
                        break;

                     default:
                        LOG.error("DataFileDownloader$DownloadFileCommand.run(): Unexpected DataFileStatus [" + fileStatus + "].  Ignoring.");
                     }
                  }
               }
            }

         if (LOG.isDebugEnabled())
            {
            LOG.debug("DataFileDownloader$DownloadFileCommand.run(): Scheduling the next download for " + delayUntilNextDownload + " " + timeUnit + " from now...");
            }
         scheduleNextFileDownload(delayUntilNextDownload, timeUnit);
         }
      }
   }
