package org.bodytrack.loggingdevice;

import org.jetbrains.annotations.NotNull;

/**
 * @author Chris Bartley (bartley@cmu.edu)
 */
public interface LoggingDeviceConfig
   {
   /** Returns the username associated with this device. */
   @NotNull
   String getUsername();

   /** Returns this device's nickname. */
   @NotNull
   String getDeviceNickname();
   }
