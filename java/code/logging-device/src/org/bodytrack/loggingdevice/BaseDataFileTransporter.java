package org.bodytrack.loggingdevice;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * <p>
 * <code>BaseDataFileTransporter</code> handles tasks common to subclasses which need to transport {@link DataFile}s.
 * </p>
 *
 * @author Chris Bartley (bartley@cmu.edu)
 */
abstract class BaseDataFileTransporter
   {
   private static final Logger LOG = Logger.getLogger(BaseDataFileTransporter.class);

   private final LoggingDevice device;
   private final DataFileManager dataFileManager;
   private final LoggingDeviceConfig loggingDeviceConfig;
   private boolean isRunning = false;
   private boolean hasBeenShutdown = false;
   private final Lock lock = new ReentrantLock();

   /**
    * Constructs a <code>BaseDataFileTransporter</code> for the given {@link LoggingDevice}.
    *
    * @throws IllegalStateException if a {@link DataFileManager} cannot be created for the given {@link LoggingDevice}.
    *
    * @see DataFileManager
    */
   protected BaseDataFileTransporter(@NotNull final LoggingDevice device) throws IllegalStateException
      {
      this.device = device;

      loggingDeviceConfig = device.getLoggingDeviceConfig();
      if (loggingDeviceConfig == null)
         {
         throw new IllegalStateException("Cannot create the DataFileManager used by the BaseDataFileTransporter because the LoggingDeviceConfig is null.");
         }

      final DataStoreServerConfig dataStoreServerConfig = device.getDataStoreServerConfig();
      if (dataStoreServerConfig == null)
         {
         throw new IllegalStateException("Cannot create the DataFileManager used by the BaseDataFileTransporter because the DataStoreServerConfig is null.");
         }

      this.dataFileManager = DataFileManager.getInstance(loggingDeviceConfig, dataStoreServerConfig);
      }

   @NotNull
   protected final LoggingDevice getDevice()
      {
      return device;
      }

   @NotNull
   protected final DataFileManager getDataFileManager()
      {
      return dataFileManager;
      }

   @NotNull
   protected final LoggingDeviceConfig getLoggingDeviceConfig()
      {
      return loggingDeviceConfig;
      }

   /**
    * Starts the <code>BaseDataFileTransporter</code>.
    *
    * @throws IllegalStateException if {@link @shutdown} has already been called.
    */
   public void startup() throws IllegalStateException
      {
      lock.lock();  // block until condition holds
      try
         {
         if (!isRunning && !hasBeenShutdown)
            {
            isRunning = true;

            performUponStartup();
            }
         else
            {
            LOG.error("BaseDataFileTransporter.startup(): Cannot startup since it's already running or has been shutdown.");
            }
         }
      finally
         {
         lock.unlock();
         }
      }

   protected abstract void performUponStartup();

   @NotNull
   protected abstract ExecutorService getExecutor();

   protected final boolean isRunning()
      {
      lock.lock();  // block until condition holds
      try
         {
         return isRunning;
         }
      finally
         {
         lock.unlock();
         }
      }

   /**
    * Shuts down the <code>BaseDataFileTransporter</code>.  Once it is shut down, it cannot be started up again.
    *
    * @see #startup()
    */
   public void shutdown()
      {
      LOG.debug("BaseDataFileTransporter.shutdown()");

      lock.lock();  // block until condition holds
      try
         {
         if (isRunning)
            {
            isRunning = false;
            hasBeenShutdown = true;

            // shut down the executor
            try
               {
               final ExecutorService executor = getExecutor();

               LOG.debug("BaseDataFileTransporter.shutdown(): Shutting down the executor");
               final List<Runnable> unexecutedTasks = executor.shutdownNow();
               LOG.debug("BaseDataFileTransporter.shutdown(): Unexecuted tasks: " + (unexecutedTasks == null ? 0 : unexecutedTasks.size()));
               LOG.debug("BaseDataFileTransporter.shutdown(): Waiting up to 30 seconds for the executor to shutdown...");
               final boolean terminatedNormally = executor.awaitTermination(30, TimeUnit.SECONDS);
               if (LOG.isDebugEnabled())
                  {
                  LOG.debug("BaseDataFileTransporter.shutdown(): Executor successfully shutdown (timed out = " + !terminatedNormally + ")");
                  }
               }
            catch (Exception e)
               {
               LOG.error("BaseDataFileTransporter.shutdown(): Exception while trying to shut down the executor", e);
               }
            }
         }
      finally
         {
         lock.unlock();
         }
      }
   }
