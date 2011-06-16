package org.bodytrack.loggingdevice.commands;

import edu.cmu.ri.createlab.serial.CreateLabSerialDeviceNoReturnValueCommandStrategy;

/**
 * @author Chris Bartley (bartley@cmu.edu)
 */
public final class DisconnectCommandStrategy extends CreateLabSerialDeviceNoReturnValueCommandStrategy
   {
   /** The command character used to disconnect from the BodyTrack Logging Device and put it back into startup mode. */
   private static final byte[] COMMAND = {'R'};

   @Override
   protected byte[] getCommand()
      {
      return COMMAND.clone();
      }
   }
