package org.bodytrack.loggingdevice;

import java.util.SortedSet;
import edu.cmu.ri.createlab.device.connectivity.BaseCreateLabDeviceConnectivityManager;
import edu.cmu.ri.createlab.serial.SerialPortEnumerator;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;

/**
 * @author Chris Bartley (bartley@cmu.edu)
 */
class LoggingDeviceConnectivityManager extends BaseCreateLabDeviceConnectivityManager<LoggingDevice>
   {
   private static final Logger LOG = Logger.getLogger(LoggingDeviceConnectivityManager.class);

   @Nullable
   @Override
   protected LoggingDevice scanForDeviceAndCreateProxy()
      {
      LOG.debug("LoggingDeviceConnectivityManager.scanForDeviceAndCreateProxy()");

      // If the user specified one or more serial ports, then just start trying to connect to it/them.  Otherwise,
      // check each available serial port for the target serial device, and connect to the first one found.  This
      // makes connection time much faster for when you know the name of the serial port.
      final SortedSet<String> availableSerialPorts;
      if (SerialPortEnumerator.didUserDefineSetOfSerialPorts())
         {
         availableSerialPorts = SerialPortEnumerator.getSerialPorts();
         }
      else
         {
         availableSerialPorts = SerialPortEnumerator.getAvailableSerialPorts();
         }

      // try the serial ports
      if ((availableSerialPorts != null) && (!availableSerialPorts.isEmpty()))
         {
         for (final String portName : availableSerialPorts)
            {
            if (LOG.isDebugEnabled())
               {
               LOG.debug("LoggingDeviceConnectivityManager.scanForDeviceAndCreateProxy(): checking serial port [" + portName + "]");
               }

            final LoggingDevice loggingDevice = LoggingDeviceFactory.create(portName);

            if (loggingDevice == null)
               {
               LOG.debug("LoggingDeviceConnectivityManager.scanForDeviceAndCreateProxy(): connection failed, maybe it's not the device we're looking for?");
               }
            else
               {
               LOG.debug("LoggingDeviceConnectivityManager.scanForDeviceAndCreateProxy(): connection established, returning LoggingDevice!");
               return loggingDevice;
               }
            }
         }
      else
         {
         LOG.debug("LoggingDeviceConnectivityManager.scanForDeviceAndCreateProxy(): No available serial ports, returning null.");
         }

      return null;
      }
   }
