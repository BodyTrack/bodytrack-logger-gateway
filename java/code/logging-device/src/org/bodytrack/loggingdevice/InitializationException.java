package org.bodytrack.loggingdevice;

/**
 * <p>
 * <code>InitializationException</code> is thrown to signify that initialization failed.
 * </p>
 *
 * @author Chris Bartley (bartley@cmu.edu)
 */
final class InitializationException extends Exception
   {
   public InitializationException()
      {
      }

   public InitializationException(final String s)
      {
      super(s);
      }

   public InitializationException(final String s, final Throwable throwable)
      {
      super(s, throwable);
      }

   public InitializationException(final Throwable throwable)
      {
      super(throwable);
      }
   }
