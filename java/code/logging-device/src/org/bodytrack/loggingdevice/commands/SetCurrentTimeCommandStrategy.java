package org.bodytrack.loggingdevice.commands;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import edu.cmu.ri.createlab.serial.CreateLabSerialDeviceNoReturnValueCommandStrategy;

/**
 * @author Chris Bartley (bartley@cmu.edu)
 */
public class SetCurrentTimeCommandStrategy extends CreateLabSerialDeviceNoReturnValueCommandStrategy
   {
   /** The command character used to send the time command. */
   private static final byte COMMAND_PREFIX = 'T';

   @Override
   protected byte[] getCommand()
      {
      // create a ByteBuffer and an IntBuffer view of it to convert the time in seconds since the epoch to a byte array
      final ByteBuffer byteBuffer = ByteBuffer.allocate(4);
      final IntBuffer intBuffer = byteBuffer.asIntBuffer();
      intBuffer.put((int)(System.currentTimeMillis() / 1000));
      final byte[] bytes = byteBuffer.array();

      final byte[] command = new byte[]{COMMAND_PREFIX,
                                        bytes[0],
                                        bytes[1],
                                        bytes[2],
                                        bytes[3]
      };

      return command.clone();
      }
   }
