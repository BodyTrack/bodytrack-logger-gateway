package org.bodytrack.loggingdevice;

import java.util.Collection;
import java.util.HashSet;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import edu.cmu.ri.createlab.device.CreateLabDevicePingFailureEventListener;
import edu.cmu.ri.createlab.serial.CreateLabSerialDeviceCommandStrategy;
import edu.cmu.ri.createlab.serial.SerialDeviceCommandExecutionQueue;
import edu.cmu.ri.createlab.serial.SerialDeviceNoReturnValueCommandExecutor;
import edu.cmu.ri.createlab.serial.SerialDeviceReturnValueCommandExecutor;
import edu.cmu.ri.createlab.serial.SerialDeviceReturnValueCommandStrategy;
import edu.cmu.ri.createlab.serial.config.BaudRate;
import edu.cmu.ri.createlab.serial.config.CharacterSize;
import edu.cmu.ri.createlab.serial.config.FlowControl;
import edu.cmu.ri.createlab.serial.config.Parity;
import edu.cmu.ri.createlab.serial.config.SerialIOConfiguration;
import edu.cmu.ri.createlab.serial.config.StopBits;
import edu.cmu.ri.createlab.util.commandexecution.CommandExecutionFailureHandler;
import edu.cmu.ri.createlab.util.thread.DaemonThreadFactory;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.bodytrack.loggingdevice.commands.DeleteFileCommandStrategy;
import org.bodytrack.loggingdevice.commands.DisconnectCommandStrategy;
import org.bodytrack.loggingdevice.commands.GetAvailableFilenamesCommandStrategy;
import org.bodytrack.loggingdevice.commands.GetFileCommandStrategy;
import org.bodytrack.loggingdevice.commands.HandshakeCommandStrategy;
import org.bodytrack.loggingdevice.commands.PingCommandStrategy;
import org.bodytrack.loggingdevice.commands.SetCurrentTimeCommandStrategy;
import org.bodytrack.loggingdevice.commands.VariableLengthStringResponseCommandStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Chris Bartley (bartley@cmu.edu)
 */
class LoggingDeviceProxy implements LoggingDevice
   {
   private static final Logger LOG = Logger.getLogger(LoggingDeviceProxy.class);
   private static final Logger CONSOLE_LOG = Logger.getLogger("ConsoleLog");

   public static final String APPLICATION_NAME = "LoggingDeviceProxy";
   private static final int DELAY_IN_SECONDS_BETWEEN_PINGS = 2;

   /**
    * Tries to create a <code>LoggingDeviceProxy</code> for the the serial port specified by the given
    * <code>serialPortName</code>. Returns <code>null</code> if the connection could not be established.
    *
    * @param serialPortName - the name of the serial port device which should be used to establish the connection
    *
    * @throws IllegalArgumentException if the <code>serialPortName</code> is <code>null</code>
    */
   @Nullable
   static LoggingDeviceProxy create(@Nullable final String serialPortName)
      {
      // a little error checking...
      if (serialPortName == null)
         {
         throw new IllegalArgumentException("The serial port name may not be null");
         }

      // create the serial port configuration
      final SerialIOConfiguration config = new SerialIOConfiguration(serialPortName,
                                                                     BaudRate.BAUD_460800,
                                                                     CharacterSize.EIGHT,
                                                                     Parity.NONE,
                                                                     StopBits.ONE,
                                                                     FlowControl.HARDWARE);

      try
         {
         // create the serial port command queue (passing in a null TimeUnit causes tasks to block until complete--no timeout)
         final SerialDeviceCommandExecutionQueue commandQueue = SerialDeviceCommandExecutionQueue.create(APPLICATION_NAME, config, -1, null);

         // see whether its creation was successful
         if (commandQueue == null)
            {
            if (LOG.isEnabledFor(Level.ERROR))
               {
               LOG.error("Failed to open serial port '" + serialPortName + "'");
               }
            }
         else
            {
            if (LOG.isDebugEnabled())
               {
               LOG.debug("Serial port '" + serialPortName + "' opened.");
               }

            // now try to do the handshake with the BodyTrack Logging Device to establish communication
            final boolean wasHandshakeSuccessful = commandQueue.executeAndReturnStatus(new HandshakeCommandStrategy());

            // see if the handshake was a success
            if (wasHandshakeSuccessful)
               {
               LOG.info("BodyTrack Logging Device handshake successful!");

               // now create and return the proxy
               return new LoggingDeviceProxy(commandQueue, serialPortName);
               }
            else
               {
               LOG.error("Failed to handshake with the BodyTrack Logging Device");
               }

            // the handshake failed, so shutdown the command queue to release the serial port
            commandQueue.shutdown();
            }
         }
      catch (Exception e)
         {
         LOG.error("Exception while trying to create the LoggingDeviceProxy", e);
         }

      return null;
      }

   private final SerialDeviceCommandExecutionQueue commandQueue;
   private final String serialPortName;
   private final CreateLabSerialDeviceCommandStrategy disconnectCommandStrategy = new DisconnectCommandStrategy();
   private final SerialDeviceReturnValueCommandStrategy<String> pingCommandStrategy = new PingCommandStrategy();
   private final SerialDeviceReturnValueCommandStrategy<String> getAvilableFilenamesCommandStrategy = new GetAvailableFilenamesCommandStrategy();
   private final CreateLabSerialDeviceCommandStrategy setCurrentTimeCommandStrategy = new SetCurrentTimeCommandStrategy();

   private final SerialDeviceReturnValueCommandExecutor<DataFile> dataFileReturnValueCommandExecutor;
   private final SerialDeviceReturnValueCommandExecutor<Boolean> booleanReturnValueCommandExecutor;
   private final SerialDeviceReturnValueCommandExecutor<String> stringReturnValueCommandExecutor;

   private final Pinger pinger = new Pinger();
   private final ScheduledExecutorService pingExecutorService = Executors.newSingleThreadScheduledExecutor(new DaemonThreadFactory(this.getClass() + ".pingExecutorService"));
   private final ScheduledFuture<?> pingScheduledFuture;
   private final Collection<CreateLabDevicePingFailureEventListener> createLabDevicePingFailureEventListeners = new HashSet<CreateLabDevicePingFailureEventListener>();

   private final LoggingDeviceConfig loggingDeviceConfig;
   private final DataStoreServerConfig dataStoreServerConfig;
   private final DataStoreConnectionConfig dataStoreConnectionConfig;

   private LoggingDeviceProxy(final SerialDeviceCommandExecutionQueue commandQueue, final String serialPortName)
      {
      this.commandQueue = commandQueue;
      this.serialPortName = serialPortName;

      final CommandExecutionFailureHandler commandExecutionFailureHandler =
            new CommandExecutionFailureHandler()
            {
            public void handleExecutionFailure()
               {
               pinger.forceFailure();
               }
            };
      dataFileReturnValueCommandExecutor = new SerialDeviceReturnValueCommandExecutor<DataFile>(commandQueue, commandExecutionFailureHandler);
      booleanReturnValueCommandExecutor = new SerialDeviceReturnValueCommandExecutor<Boolean>(commandQueue, commandExecutionFailureHandler);
      stringReturnValueCommandExecutor = new SerialDeviceReturnValueCommandExecutor<String>(commandQueue, commandExecutionFailureHandler);
      final SerialDeviceNoReturnValueCommandExecutor noReturnValueCommandExecutor = new SerialDeviceNoReturnValueCommandExecutor(commandQueue, commandExecutionFailureHandler);

      // before doing anything else, we need to tell the device the current time--keep retrying until successful
      boolean wasSetTimeSuccessful;
      int numAttemptsToSetTime = 0;
      do
         {
         wasSetTimeSuccessful = noReturnValueCommandExecutor.execute(setCurrentTimeCommandStrategy);
         numAttemptsToSetTime++;
         if (!wasSetTimeSuccessful)
            {
            final String message = "Failed to set the time on the device (" + numAttemptsToSetTime + " failed attempt(s))";
            LOG.error(message);
            CONSOLE_LOG.error(message);
            try
               {
               //noinspection BusyWait
               Thread.sleep(500);
               }
            catch (InterruptedException e)
               {
               LOG.error("InterruptedException while sleeping", e);
               }
            }
         }
      while (!wasSetTimeSuccessful);

      // we cache all the config values since the chances of the user reconfiguring the device while the program is
      // running is low and isn't supported by the devices anyway
      loggingDeviceConfig =
            new RetryingActionExecutor<LoggingDeviceConfig>()
            {
            @Override
            @Nullable
            protected LoggingDeviceConfig executionWorkhorse()
               {
               final String msg = "Reading username and device nickname from device...";
               CONSOLE_LOG.info(msg);
               if (LOG.isInfoEnabled())
                  {
                  LOG.info("LoggingDeviceProxy.executionWorkhorse(): " + msg);
                  }
               final String username = trim(stringReturnValueCommandExecutor.execute(new VariableLengthStringResponseCommandStrategy('U')));
               final String deviceNickname = trim(stringReturnValueCommandExecutor.execute(new VariableLengthStringResponseCommandStrategy('N')));

               if (isNonNullAndNonEmpty(username) && isNonNullAndNonEmpty(deviceNickname))
                  {
                  return new LoggingDeviceConfigImpl(username, deviceNickname);
                  }

               LOG.error("LoggingDeviceProxy.executionWorkhorse(): failed to retrieve username [" + username + "] and/or deviceNickname [" + deviceNickname + "].  Returning null LoggingDeviceConfig.");
               return null;
               }
            }.execute();

      if (loggingDeviceConfig == null)
         {
         final String message = "Failed to read username and device nickname from device!";
         LOG.error(message);
         CONSOLE_LOG.error(message);
         }
      else
         {
         final String message = "Successfully read username and device nickname from device.";
         LOG.info(message);
         CONSOLE_LOG.info(message);
         }

      dataStoreServerConfig =
            new RetryingActionExecutor<DataStoreServerConfig>()
            {
            @Override
            @Nullable
            protected DataStoreServerConfig executionWorkhorse()
               {
               final String msg = "Reading server name and port from device...";
               CONSOLE_LOG.info(msg);
               if (LOG.isInfoEnabled())
                  {
                  LOG.info("LoggingDeviceProxy.executionWorkhorse(): " + msg);
                  }
               final String serverName = trim(stringReturnValueCommandExecutor.execute(new VariableLengthStringResponseCommandStrategy('V')));
               final String serverPort = trim(stringReturnValueCommandExecutor.execute(new VariableLengthStringResponseCommandStrategy('O')));

               if (isNonNullAndNonEmpty(serverName) && isNonNullAndNonEmpty(serverPort))
                  {
                  return new DataStoreServerConfigImpl(serverName, serverPort);
                  }
               LOG.error("LoggingDeviceProxy.executionWorkhorse(): failed to retrieve serverName [" + serverName + "] and/or serverPort [" + serverPort + "].  Returning null DataStoreServerConfig.");
               return null;
               }
            }.execute();

      if (dataStoreServerConfig == null)
         {
         final String message = "Failed to read server name and port from device!";
         LOG.error(message);
         CONSOLE_LOG.error(message);
         }
      else
         {
         final String message = "Successfully read server name and port from device.";
         LOG.info(message);
         CONSOLE_LOG.info(message);
         }

      dataStoreConnectionConfig =
            new RetryingActionExecutor<DataStoreConnectionConfig>()
            {
            @Override
            protected DataStoreConnectionConfig executionWorkhorse()
               {
               final String msg = "Reading wifi config from device...";
               CONSOLE_LOG.info(msg);
               if (LOG.isInfoEnabled())
                  {
                  LOG.info("LoggingDeviceProxy.executionWorkhorse(): " + msg);
                  }
               final String wirelessSsid = trim(stringReturnValueCommandExecutor.execute(new VariableLengthStringResponseCommandStrategy('S')));
               final WirelessAuthorizationType wirelessAuthorizationType = WirelessAuthorizationType.findById(trim(stringReturnValueCommandExecutor.execute(new VariableLengthStringResponseCommandStrategy('A'))));
               final String wirelessAuthorizationKey = trim(stringReturnValueCommandExecutor.execute(new VariableLengthStringResponseCommandStrategy('K')));

               if (isNonNullAndNonEmpty(wirelessSsid) && wirelessAuthorizationType != null && isNonNullAndNonEmpty(wirelessAuthorizationKey))
                  {
                  return new DataStoreConnectionConfigImpl(wirelessSsid, wirelessAuthorizationType, wirelessAuthorizationKey);
                  }
               LOG.error("LoggingDeviceProxy.executionWorkhorse(): failed to retrieve wirelessSsid [" + wirelessSsid + "], wirelessAuthorizationType [" + wirelessAuthorizationType + "], and/or wirelessAuthorizationKey [" + wirelessAuthorizationKey + "].  Returning null DataStoreConnectionConfig.");
               return null;
               }
            }.execute();

      if (dataStoreConnectionConfig == null)
         {
         final String message = "Failed to read wifi config from device!";
         LOG.error(message);
         CONSOLE_LOG.error(message);
         }
      else
         {
         final String message = "Successfully read wifi config from device.";
         LOG.info(message);
         CONSOLE_LOG.info(message);
         }

      // schedule periodic pings
      pingScheduledFuture = pingExecutorService.scheduleAtFixedRate(pinger,
                                                                    DELAY_IN_SECONDS_BETWEEN_PINGS, // delay before first ping
                                                                    DELAY_IN_SECONDS_BETWEEN_PINGS, // delay between pings
                                                                    TimeUnit.SECONDS);
      }

   /** Trims the given String and returns it.  Returns <code>null</code> if the given String is <code>null</code>. */
   @Nullable
   private String trim(@Nullable final String s)
      {
      if (s != null)
         {
         return s.trim();
         }
      return null;
      }

   private boolean isNonNullAndNonEmpty(@Nullable final String s)
      {
      return s != null && s.length() > 0;
      }

   public String getPortName()
      {
      return serialPortName;
      }

   @Override
   public void addCreateLabDevicePingFailureEventListener(final CreateLabDevicePingFailureEventListener listener)
      {
      if (listener != null)
         {
         createLabDevicePingFailureEventListeners.add(listener);
         }
      }

   @Override
   public void removeCreateLabDevicePingFailureEventListener(final CreateLabDevicePingFailureEventListener listener)
      {
      if (listener != null)
         {
         createLabDevicePingFailureEventListeners.remove(listener);
         }
      }

   @Override
   @Nullable
   public SortedSet<String> getAvailableFilenames()
      {
      final String commaDelimitedFilenames = stringReturnValueCommandExecutor.execute(getAvilableFilenamesCommandStrategy);
      if (commaDelimitedFilenames != null)
         {
         final SortedSet<String> availableFiles = new TreeSet<String>();

         final String[] filenames = commaDelimitedFilenames.split(",");
         for (final String rawFilename : filenames)
            {
            if (rawFilename != null)
               {
               final String filename = rawFilename.trim();
               if (filename.length() > 0)
                  {
                  availableFiles.add(filename);
                  }
               }
            }

         return availableFiles;
         }

      return null;
      }

   @Override
   @Nullable
   public DataFile getFile(final String filename) throws NoSuchFileException
      {
      DataFile dataFile = null;

      if (filename != null)
         {
         try
            {
            // pause the pinger since file transfers may take a long time
            pinger.setPaused(true);

            // get the file
            dataFile = dataFileReturnValueCommandExecutor.execute(new GetFileCommandStrategy(filename));
            }
         catch (Exception e)
            {
            LOG.error("LoggingDeviceProxy.downloadFile(): Exception while trying to download file [" + filename + "]", e);
            }
         finally
            {
            // make sure the pinger is unpaused
            pinger.setPaused(false);
            }
         }

      if (dataFile != null && dataFile.isEmpty())
         {
         throw new NoSuchFileException("File '" + filename + "' not found");
         }

      return dataFile;
      }

   @Override
   public boolean deleteFile(final String filename)
      {
      if (filename != null && filename.length() > 0)
         {
         return booleanReturnValueCommandExecutor.execute(new DeleteFileCommandStrategy(filename));
         }
      else
         {
         LOG.error("LoggingDeviceProxy.deleteFile(): filename cannot be null or empty.");
         }
      return false;
      }

   @Override
   @Nullable
   public LoggingDeviceConfig getLoggingDeviceConfig()
      {
      return loggingDeviceConfig;
      }

   @Override
   @Nullable
   public DataStoreServerConfig getDataStoreServerConfig()
      {
      return dataStoreServerConfig;
      }

   @Override
   @Nullable
   public DataStoreConnectionConfig getDataStoreConnectionConfig()
      {
      return dataStoreConnectionConfig;
      }

   public void disconnect()
      {
      disconnect(true);
      }

   private void disconnect(final boolean willAddDisconnectCommandToQueue)
      {
      if (LOG.isDebugEnabled())
         {
         LOG.debug("LoggingDeviceProxy.disconnect(" + willAddDisconnectCommandToQueue + ")");
         }

      // turn off the pinger
      try
         {
         pingScheduledFuture.cancel(false);
         pingExecutorService.shutdownNow();
         LOG.debug("LoggingDeviceProxy.disconnect(): Successfully shut down the BodyTrack Logging Device pinger.");
         }
      catch (Exception e)
         {
         LOG.error("LoggingDeviceProxy.disconnect(): Exception caught while trying to shut down pinger", e);
         }

      // optionally send goodbye command to the BodyTrack Logging Device
      if (willAddDisconnectCommandToQueue)
         {
         LOG.debug("LoggingDeviceProxy.disconnect(): Now attempting to send the disconnect command to the BodyTrack Logging Device");
         try
            {
            if (commandQueue.executeAndReturnStatus(disconnectCommandStrategy))
               {
               LOG.debug("LoggingDeviceProxy.disconnect(): Successfully disconnected from the BodyTrack Logging Device.");
               }
            else
               {
               LOG.error("LoggingDeviceProxy.disconnect(): Failed to disconnect from the BodyTrack Logging Device.");
               }
            }
         catch (Exception e)
            {
            LOG.error("Exception caught while trying to execute the disconnect", e);
            }
         }

      // shut down the command queue, which closes the serial port
      try
         {
         LOG.debug("LoggingDeviceProxy.disconnect(): shutting down the SerialDeviceCommandExecutionQueue...");
         commandQueue.shutdown();
         LOG.debug("LoggingDeviceProxy.disconnect(): done shutting down the SerialDeviceCommandExecutionQueue");
         }
      catch (Exception e)
         {
         LOG.error("LoggingDeviceProxy.disconnect(): Exception while trying to shut down the SerialDeviceCommandExecutionQueue", e);
         }
      }

   private abstract static class RetryingActionExecutor<ReturnType>
      {
      private static final int MAX_RETRIES = 3;

      /**
       * Executes an action and returns the result, retrying if necessary up to three times.  Returns <code>null</code>
       * only if it fails to get a non-<code>null</code> result.
       */
      @SuppressWarnings({"BusyWait"})
      @Nullable
      protected final ReturnType execute()
         {
         int retryCount = 0;
         do
            {
            try
               {
               final ReturnType val = executionWorkhorse();
               if (val != null)
                  {
                  return val;
                  }
               }
            catch (Exception e)
               {
               LOG.error("Exception while executing the action", e);
               }
            retryCount++;
            if (retryCount < MAX_RETRIES)
               {
               try
                  {
                  Thread.sleep(200);
                  }
               catch (InterruptedException e)
                  {
                  LOG.error("InterruptedException while sleeping", e);
                  }
               }
            }
         while (retryCount < MAX_RETRIES);

         return null;
         }

      @Nullable
      protected abstract ReturnType executionWorkhorse();
      }

   private static class LoggingDeviceConfigImpl implements LoggingDeviceConfig
      {
      private final String username;
      private final String deviceNickname;

      private LoggingDeviceConfigImpl(@NotNull final String username, @NotNull final String deviceNickname)
         {
         this.username = username;
         this.deviceNickname = deviceNickname;
         }

      @Override
      @NotNull
      public String getUsername()
         {
         return username;
         }

      @Override
      @NotNull
      public String getDeviceNickname()
         {
         return deviceNickname;
         }

      @Override
      public boolean equals(final Object o)
         {
         if (this == o)
            {
            return true;
            }
         if (o == null || getClass() != o.getClass())
            {
            return false;
            }

         final LoggingDeviceConfigImpl that = (LoggingDeviceConfigImpl)o;

         if (deviceNickname != null ? !deviceNickname.equals(that.deviceNickname) : that.deviceNickname != null)
            {
            return false;
            }
         if (username != null ? !username.equals(that.username) : that.username != null)
            {
            return false;
            }

         return true;
         }

      @Override
      public int hashCode()
         {
         int result = username != null ? username.hashCode() : 0;
         result = 31 * result + (deviceNickname != null ? deviceNickname.hashCode() : 0);
         return result;
         }

      @Override
      public String toString()
         {
         final StringBuilder sb = new StringBuilder();
         sb.append("LoggingDeviceConfig");
         sb.append("{username='").append(username).append('\'');
         sb.append(", deviceNickname='").append(deviceNickname).append('\'');
         sb.append('}');
         return sb.toString();
         }
      }

   private static class DataStoreServerConfigImpl implements DataStoreServerConfig
      {
      private final String serverName;
      private final String serverPort;

      private DataStoreServerConfigImpl(@NotNull final String serverName, @NotNull final String serverPort)
         {
         this.serverName = serverName;
         this.serverPort = serverPort;
         }

      @Override
      @NotNull
      public String getServerName()
         {
         return serverName;
         }

      @Override
      @NotNull
      public String getServerPort()
         {
         return serverPort;
         }

      @Override
      public boolean equals(final Object o)
         {
         if (this == o)
            {
            return true;
            }
         if (o == null || getClass() != o.getClass())
            {
            return false;
            }

         final DataStoreServerConfigImpl that = (DataStoreServerConfigImpl)o;

         if (serverName != null ? !serverName.equals(that.serverName) : that.serverName != null)
            {
            return false;
            }
         if (serverPort != null ? !serverPort.equals(that.serverPort) : that.serverPort != null)
            {
            return false;
            }

         return true;
         }

      @Override
      public int hashCode()
         {
         int result = serverName != null ? serverName.hashCode() : 0;
         result = 31 * result + (serverPort != null ? serverPort.hashCode() : 0);
         return result;
         }

      @Override
      public String toString()
         {
         final StringBuilder sb = new StringBuilder();
         sb.append("DataStoreServerConfig");
         sb.append("{serverName='").append(serverName).append('\'');
         sb.append(", serverPort='").append(serverPort).append('\'');
         sb.append('}');
         return sb.toString();
         }
      }

   private static class DataStoreConnectionConfigImpl implements DataStoreConnectionConfig
      {
      private final String wirelessSsid;
      private final WirelessAuthorizationType wirelessAuthorizationType;
      private final String wirelessAuthorizationKey;

      private DataStoreConnectionConfigImpl(@NotNull final String wirelessSsid,
                                            @NotNull final WirelessAuthorizationType wirelessAuthorizationType,
                                            @NotNull final String wirelessAuthorizationKey)
         {
         this.wirelessSsid = wirelessSsid;
         this.wirelessAuthorizationType = wirelessAuthorizationType;
         this.wirelessAuthorizationKey = wirelessAuthorizationKey;
         }

      @NotNull
      @Override
      public String getWirelessSsid()
         {
         return wirelessSsid;
         }

      @NotNull
      @Override
      public WirelessAuthorizationType getWirelessAuthorizationType()
         {
         return wirelessAuthorizationType;
         }

      @NotNull
      @Override
      public String getWirelessAuthorizationKey()
         {
         return wirelessAuthorizationKey;
         }

      @Override
      public boolean equals(final Object o)
         {
         if (this == o)
            {
            return true;
            }
         if (o == null || getClass() != o.getClass())
            {
            return false;
            }

         final DataStoreConnectionConfigImpl that = (DataStoreConnectionConfigImpl)o;

         if (wirelessAuthorizationKey != null ? !wirelessAuthorizationKey.equals(that.wirelessAuthorizationKey) : that.wirelessAuthorizationKey != null)
            {
            return false;
            }
         if (wirelessAuthorizationType != that.wirelessAuthorizationType)
            {
            return false;
            }
         if (wirelessSsid != null ? !wirelessSsid.equals(that.wirelessSsid) : that.wirelessSsid != null)
            {
            return false;
            }

         return true;
         }

      @Override
      public int hashCode()
         {
         int result = wirelessSsid != null ? wirelessSsid.hashCode() : 0;
         result = 31 * result + (wirelessAuthorizationType != null ? wirelessAuthorizationType.hashCode() : 0);
         result = 31 * result + (wirelessAuthorizationKey != null ? wirelessAuthorizationKey.hashCode() : 0);
         return result;
         }

      @Override
      public String toString()
         {
         final StringBuilder sb = new StringBuilder();
         sb.append("DataStoreConnectionConfig");
         sb.append("{wirelessSsid='").append(wirelessSsid).append('\'');
         sb.append(", wirelessAuthorizationType=").append(wirelessAuthorizationType);
         sb.append(", wirelessAuthorizationKey='").append(wirelessAuthorizationKey).append('\'');
         sb.append('}');
         return sb.toString();
         }
      }

   private class Pinger implements Runnable
      {
      private static final int NUM_COUNTS_BETWEEN_TIME_PINGS = 10;
      private boolean isPaused = false;
      private final Lock lock = new ReentrantLock();
      private int counter = 0;

      public void setPaused(final boolean isPaused)
         {
         lock.lock();  // block until condition holds
         try
            {
            this.isPaused = isPaused;
            if (LOG.isDebugEnabled())
               {
               LOG.debug("LoggingDeviceProxy$Pinger.setPaused(): pinger paused = [" + isPaused + "]");
               }
            }
         finally
            {
            lock.unlock();
            }
         }

      public void run()
         {
         lock.lock();  // block until condition holds
         try
            {
            if (isPaused)
               {
               LOG.trace("LoggingDeviceProxy$Pinger.run(): not pinging because the pinger is paused");
               }
            else
               {
               // Ping the device.  We typically just request the username and make sure it was successful, but every
               // NUM_COUNTS_BETWEEN_TIME_PINGS we send the time to make sure the device has the correct time
               final boolean pingSuccessful = commandQueue.executeAndReturnStatus(counter == NUM_COUNTS_BETWEEN_TIME_PINGS ? setCurrentTimeCommandStrategy : pingCommandStrategy);

               // if the ping failed, then we know we have a problem, so disconnect (which
               // probably won't work) and then notify the listeners
               if (!pingSuccessful)
                  {
                  handlePingFailure();
                  }

               if (counter >= NUM_COUNTS_BETWEEN_TIME_PINGS)
                  {
                  counter = 0;
                  }
               else
                  {
                  counter++;
                  }
               }
            }
         catch (Exception e)
            {
            LOG.error("LoggingDeviceProxy$Pinger.run(): Exception caught while executing the pinger", e);
            }
         finally
            {
            lock.unlock();
            }
         }

      private void handlePingFailure()
         {
         try
            {
            LOG.debug("LoggingDeviceProxy$Pinger.handlePingFailure(): Ping failed.  Attempting to disconnect...");
            disconnect(false);
            LOG.debug("LoggingDeviceProxy$Pinger.handlePingFailure(): Done disconnecting from the BodyTrack Logging Device");
            }
         catch (Exception e)
            {
            LOG.error("LoggingDeviceProxy$Pinger.handlePingFailure(): Exeption caught while trying to disconnect from the BodyTrack Logging Device", e);
            }

         if (LOG.isDebugEnabled())
            {
            LOG.debug("LoggingDeviceProxy$Pinger.handlePingFailure(): Notifying " + createLabDevicePingFailureEventListeners.size() + " listeners of ping failure...");
            }
         for (final CreateLabDevicePingFailureEventListener listener : createLabDevicePingFailureEventListeners)
            {
            try
               {
               if (LOG.isDebugEnabled())
                  {
                  LOG.debug("   LoggingDeviceProxy$Pinger.handlePingFailure(): Notifying " + listener);
                  }
               listener.handlePingFailureEvent();
               }
            catch (Exception e)
               {
               LOG.error("LoggingDeviceProxy$Pinger.handlePingFailure(): Exeption caught while notifying SerialDevicePingFailureEventListener", e);
               }
            }
         }

      private void forceFailure()
         {
         handlePingFailure();
         }
      }
   }
