package nodomain.freeyourgadget.gadgetbridge.service.btle;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;


/**
 * Provides methods to convert standard BLE units to byte sequences and vice versa.
 */
public class BLETypeConversions {
    /**
     * Converts a timestamp to the byte sequence to be sent to the current time characteristic
     *
     * @param timestamp
     * @return
     * @see GattCharacteristic#UUID_CHARACTERISTIC_CURRENT_TIME
     */
    public static byte[] calendarToRawBytes(Calendar timestamp, boolean honorDeviceTimeOffset) {

        byte[] year = fromUint16(timestamp.get(Calendar.YEAR));
        return new byte[] {
                year[0],
                year[1],
                fromUint8(timestamp.get(Calendar.MONTH) + 1),
                fromUint8(timestamp.get(Calendar.DATE)),
                fromUint8(timestamp.get(Calendar.HOUR_OF_DAY)),
                fromUint8(timestamp.get(Calendar.MINUTE)),
                fromUint8(timestamp.get(Calendar.SECOND)),
                dayOfWeekToRawBytes(timestamp),
                0, // fractions256 (not set)
                // 0 (DST offset?) Mi2
                // k (tz) Mi2
        };
    }

    /**
     * Similar to calendarToRawBytes, but only up to (and including) the MINUTES.
     * @param timestamp
     * @param honorDeviceTimeOffset
     * @return
     */
    public static byte[] shortCalendarToRawBytes(Calendar timestamp, boolean honorDeviceTimeOffset) {

        byte[] year = fromUint16(timestamp.get(Calendar.YEAR));
        return new byte[] {
                year[0],
                year[1],
                fromUint8(timestamp.get(Calendar.MONTH) + 1),
                fromUint8(timestamp.get(Calendar.DATE)),
                fromUint8(timestamp.get(Calendar.HOUR_OF_DAY)),
                fromUint8(timestamp.get(Calendar.MINUTE))
        };
    }

    private static int getMiBand2TimeZone(int rawOffset) {
        int offsetMinutes = rawOffset / 1000 / 60;
        rawOffset = offsetMinutes < 0 ? -1 : 1;
        offsetMinutes = Math.abs(offsetMinutes);
        int offsetHours = offsetMinutes / 60;
        rawOffset *= offsetMinutes % 60 / 15 + offsetHours * 4;
        return rawOffset;
    }

    private static byte dayOfWeekToRawBytes(Calendar cal) {
        int calValue = cal.get(Calendar.DAY_OF_WEEK);
        switch (calValue) {
            case Calendar.SUNDAY:
                return 7;
            default:
                return (byte) (calValue - 1);
        }
    }

    public static int toUint16(byte... bytes) {
        return (bytes[0] & 0xff) | ((bytes[1] & 0xff) << 8);
    }

    public static byte[] fromUint16(int value) {
        return new byte[] {
                (byte) (value & 0xff),
                (byte) ((value >> 8) & 0xff),
        };
    }

    public static byte[] fromUint24(int value) {
        return new byte[] {
                (byte) (value & 0xff),
                (byte) ((value >> 8) & 0xff),
                (byte) ((value >> 16) & 0xff),
        };
    }

    public static byte[] fromUint32(int value) {
        return new byte[] {
                (byte) (value & 0xff),
                (byte) ((value >> 8) & 0xff),
                (byte) ((value >> 16) & 0xff),
                (byte) ((value >> 24) & 0xff),
        };
    }

    public static byte fromUint8(int value) {
        return (byte) (value & 0xff);
    }

    /**
     * Creates a calendar object representing the current date and time.
     */
    public static GregorianCalendar createCalendar() {
        return new GregorianCalendar();
    }

    public static byte[] join(byte[] start, byte[] end) {
        if (start == null || start.length == 0) {
            return end;
        }
        if (end == null || end.length == 0) {
            return start;
        }

        byte[] result = new byte[start.length + end.length];
        System.arraycopy(start, 0, result, 0, start.length);
        System.arraycopy(end, 0, result, start.length, end.length);
        return result;
    }

    public static byte[] calendarToLocalTimeBytes(GregorianCalendar now) {
        byte[] result = new byte[2];
        result[0] = mapTimeZone(now.getTimeZone());
        result[1] = mapDstOffset(now);
        return result;
    }

    /**
     * https://www.bluetooth.com/specifications/gatt/viewer?attributeXmlFile=org.bluetooth.characteristic.time_zone.xml
     * @param timeZone
     * @return sint8 value from -48..+56
     */
    public static byte mapTimeZone(TimeZone timeZone) {
        int utcOffsetInHours =  (timeZone.getRawOffset() / (1000 * 60 * 60));
        return (byte) (utcOffsetInHours * 4);
    }

    /**
     * https://www.bluetooth.com/specifications/gatt/viewer?attributeXmlFile=org.bluetooth.characteristic.dst_offset.xml
     * @param now
     * @return the DST offset for the given time; 0 if none; 255 if unknown
     */
    public static byte mapDstOffset(Calendar now) {
        TimeZone timeZone = now.getTimeZone();
        int dstSavings = timeZone.getDSTSavings();
        if (dstSavings == 0) {
            return 0;
        }
        if (timeZone.inDaylightTime(now.getTime())) {
            int dstInMinutes = dstSavings / (1000 * 60);
            switch (dstInMinutes) {
                case 30:
                    return 2;
                case 60:
                    return 4;
                case 120:
                    return 8;
            }
            return fromUint8(255); // unknown
        }
        return 0;
    }
}
