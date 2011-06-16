package org.bodytrack.loggingdevice;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * <p>
 * <code>WirelessAuthorizationType</code> defines the various wireless authorization types supported by BodyTrack
 * logging devices.
 * </p>
 *
 * @author Chris Bartley (bartley@cmu.edu)
 */
public enum WirelessAuthorizationType
   {
      OPEN("Open", 0),
      WEP_64("WEP-64", 5),
      WEP_128("WEP-128", 1),
      WPA1("WPA1", 2),
      WPA2_PSK("WPA2-PSK", 4),
      MIXED_WPA1_AND_WPA2_PSK("Mixed WPA1 & WPA2-PSK", 3),
      ADHOC("Ad Hoc", 6);

   private static final Map<String, WirelessAuthorizationType> STRING_ID_TO_TYPE_MAP;
   private static final Map<Integer, WirelessAuthorizationType> INTEGER_ID_TO_TYPE_MAP;

   static
      {
      final Map<String, WirelessAuthorizationType> stringIdToTypeMap = new HashMap<String, WirelessAuthorizationType>();
      final Map<Integer, WirelessAuthorizationType> integerIdToTypeMap = new HashMap<Integer, WirelessAuthorizationType>();
      for (final WirelessAuthorizationType wirelessAuthorizationType : WirelessAuthorizationType.values())
         {
         stringIdToTypeMap.put(String.valueOf(wirelessAuthorizationType.getId()), wirelessAuthorizationType);
         integerIdToTypeMap.put(wirelessAuthorizationType.getId(), wirelessAuthorizationType);
         }
      STRING_ID_TO_TYPE_MAP = Collections.unmodifiableMap(stringIdToTypeMap);
      INTEGER_ID_TO_TYPE_MAP = Collections.unmodifiableMap(integerIdToTypeMap);
      }

   /**
    * Returns the <code>WirelessAuthorizationType</code> associted with the given id, or <code>null</code> if the id
    * is invalid.
    */
   @Nullable
   public static WirelessAuthorizationType findById(final String id)
      {
      return STRING_ID_TO_TYPE_MAP.get(id);
      }

   /**
    * Returns the <code>WirelessAuthorizationType</code> associted with the given id, or <code>null</code> if the id
    * is invalid.
    */
   @Nullable
   public static WirelessAuthorizationType findById(final int id)
      {
      return INTEGER_ID_TO_TYPE_MAP.get(id);
      }

   private final String name;
   private final int id;

   WirelessAuthorizationType(final String name, final int id)
      {
      this.name = name;
      this.id = id;
      }

   @NotNull
   public String getName()
      {
      return name;
      }

   public int getId()
      {
      return id;
      }

   public String toString()
      {
      return name;
      }
   }
