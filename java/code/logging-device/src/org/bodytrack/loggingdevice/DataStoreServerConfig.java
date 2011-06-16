package org.bodytrack.loggingdevice;

import org.jetbrains.annotations.NotNull;

/**
 * @author Chris Bartley (bartley@cmu.edu)
 */
public interface DataStoreServerConfig
   {
   /** Returns the server name to which this device is configured to upload. */
   @NotNull
   String getServerName();

   /** Returns the server port to which this device is configured to upload. */
   @NotNull
   String getServerPort();
   }
