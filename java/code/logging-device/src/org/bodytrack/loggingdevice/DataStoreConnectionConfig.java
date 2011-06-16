package org.bodytrack.loggingdevice;

import org.jetbrains.annotations.NotNull;

/**
 * @author Chris Bartley (bartley@cmu.edu)
 */
public interface DataStoreConnectionConfig
   {
   /** Returns the wireless SSID that this device is configured to use. */
   @NotNull
   String getWirelessSsid();

   /** Returns the {@link WirelessAuthorizationType} that this device is configured to use. */
   @NotNull
   WirelessAuthorizationType getWirelessAuthorizationType();

   /** Returns the wireless authorization key that this device is configured to use. */
   @NotNull
   String getWirelessAuthorizationKey();
   }
