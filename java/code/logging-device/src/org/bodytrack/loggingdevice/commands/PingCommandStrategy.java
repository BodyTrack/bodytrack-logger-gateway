package org.bodytrack.loggingdevice.commands;

/**
 * @author Chris Bartley (bartley@cmu.edu)
 */
public class PingCommandStrategy extends VariableLengthStringResponseCommandStrategy
   {
   public PingCommandStrategy()
      {
      // For pings, we just request the username
      super('U');
      }
   }
