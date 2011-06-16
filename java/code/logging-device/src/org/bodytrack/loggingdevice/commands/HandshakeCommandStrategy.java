package org.bodytrack.loggingdevice.commands;

import java.util.concurrent.TimeUnit;
import edu.cmu.ri.createlab.serial.CreateLabSerialDeviceHandshakeCommandStrategy;

/**
 * @author Chris Bartley (bartley@cmu.edu)
 */
public final class HandshakeCommandStrategy extends CreateLabSerialDeviceHandshakeCommandStrategy
   {
   /** The pattern of characters to look for in the BodyTrack Logging Device's startup mode "song" */
   private static final byte[] STARTUP_MODE_SONG_CHARACTERS = {'B', 'S'};

   /** The pattern of characters to send to put the BodyTrack Logging Device into receive mode. */
   private static final byte[] RECEIVE_MODE_CHARACTERS = {'B', 'T'};

   public HandshakeCommandStrategy()
      {
      super(5, TimeUnit.SECONDS);
      }

   @Override
   protected byte[] getReceiveModeCharacters()
      {
      return RECEIVE_MODE_CHARACTERS.clone();
      }

   @Override
   protected byte[] getStartupModeCharacters()
      {
      return STARTUP_MODE_SONG_CHARACTERS.clone();
      }
   }
