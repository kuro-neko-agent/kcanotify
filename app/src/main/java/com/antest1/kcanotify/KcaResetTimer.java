package com.antest1.kcanotify;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/**
 * Phase 9E: Server Reset Countdown.
 * Computes next reset times in JST (Asia/Tokyo) for practice, daily quest,
 * weekly, quarterly, monthly, senka cut-off, and EO resets.
 */
public class KcaResetTimer {

    public static final int TYPE_PRACTICE  = 0;
    public static final int TYPE_DAILY     = 1;
    public static final int TYPE_WEEKLY    = 2;
    public static final int TYPE_QUARTERLY = 3;
    public static final int TYPE_MONTHLY   = 4;
    public static final int TYPE_SENKA     = 5;
    public static final int TYPE_EO        = 6;

    public static final int[] ALL_TYPES = {
        TYPE_PRACTICE, TYPE_DAILY, TYPE_WEEKLY, TYPE_QUARTERLY,
        TYPE_MONTHLY, TYPE_SENKA, TYPE_EO
    };

    private static final TimeZone JST = TimeZone.getTimeZone("Asia/Tokyo");

    public static class ResetEntry {
        public final int type;
        public final long msUntilReset; // milliseconds from now

        ResetEntry(int type, long msUntilReset) {
            this.type = type;
            this.msUntilReset = msUntilReset;
        }
    }

    /** Returns a list of ResetEntry for the given enabled types, in the same order. */
    public static List<ResetEntry> getResetEntries(int[] types) {
        long nowMs = System.currentTimeMillis();
        List<ResetEntry> result = new ArrayList<>();
        for (int type : types) {
            long next = getNextResetMs(type, nowMs);
            result.add(new ResetEntry(type, next - nowMs));
        }
        return result;
    }

    /**
     * Returns the epoch-ms of the next occurrence of the given reset type,
     * at or after nowMs.
     */
    public static long getNextResetMs(int type, long nowMs) {
        Calendar jst = Calendar.getInstance(JST);
        jst.setTimeInMillis(nowMs);

        switch (type) {
            case TYPE_PRACTICE:
                return nextDailyAtHours(jst, new int[]{3, 15});

            case TYPE_DAILY:
                return nextDailyAtHour(jst, 5);

            case TYPE_WEEKLY:
                return nextWeeklyOnMonday5(jst);

            case TYPE_QUARTERLY:
                return nextQuarterly5(jst);

            case TYPE_MONTHLY:
                return nextMonthly1At5(jst);

            case TYPE_SENKA:
                return nextSenkaReset(jst);

            case TYPE_EO:
                return nextEoReset(jst);

            default:
                return nowMs;
        }
    }

    /** Next occurrence of any of the given hours (0-23) in JST, truncated to the hour. */
    private static long nextDailyAtHours(Calendar now, int[] hours) {
        int nowHour = now.get(Calendar.HOUR_OF_DAY);
        int nowMin  = now.get(Calendar.MINUTE);
        int nowSec  = now.get(Calendar.SECOND);

        for (int h : hours) {
            if (h > nowHour || (h == nowHour && (nowMin > 0 || nowSec > 0))) {
                // Still today — but only if strictly after now
                if (h > nowHour) {
                    return buildJstEpoch(now, 0, 0, h, 0, 0);
                }
            }
            if (h == nowHour && nowMin == 0 && nowSec == 0) {
                return now.getTimeInMillis(); // exactly on the reset second
            }
        }

        // Wrap to tomorrow, first candidate
        return buildJstEpochNextDay(now, hours[0], 0, 0);
    }

    private static long nextDailyAtHour(Calendar now, int hour) {
        return nextDailyAtHours(now, new int[]{hour});
    }

    /** Next Monday 05:00 JST. */
    private static long nextWeeklyOnMonday5(Calendar now) {
        Calendar c = (Calendar) now.clone();
        c.set(Calendar.HOUR_OF_DAY, 5);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);

        int dow = c.get(Calendar.DAY_OF_WEEK); // Sunday=1…Saturday=7
        int daysUntilMonday = (Calendar.MONDAY - dow + 7) % 7;
        if (daysUntilMonday == 0 && now.getTimeInMillis() >= c.getTimeInMillis()) {
            daysUntilMonday = 7;
        }
        c.add(Calendar.DAY_OF_YEAR, daysUntilMonday);
        return c.getTimeInMillis();
    }

    /** Next 1st-of-quarter 05:00 JST (Mar/Jun/Sep/Dec). */
    private static long nextQuarterly5(Calendar now) {
        int[] quarterMonths = {Calendar.MARCH, Calendar.JUNE,
                               Calendar.SEPTEMBER, Calendar.DECEMBER};
        int year = now.get(Calendar.YEAR);
        Calendar c = Calendar.getInstance(JST);

        for (int pass = 0; pass < 2; pass++) {
            for (int m : quarterMonths) {
                c.set(year, m, 1, 5, 0, 0);
                c.set(Calendar.MILLISECOND, 0);
                if (c.getTimeInMillis() > now.getTimeInMillis()) {
                    return c.getTimeInMillis();
                }
            }
            year++;
        }
        return now.getTimeInMillis();
    }

    /** Next 1st of month 05:00 JST. */
    private static long nextMonthly1At5(Calendar now) {
        Calendar c = Calendar.getInstance(JST);
        int year  = now.get(Calendar.YEAR);
        int month = now.get(Calendar.MONTH);

        // Try this month first
        c.set(year, month, 1, 5, 0, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() > now.getTimeInMillis()) {
            return c.getTimeInMillis();
        }
        // Next month
        c.add(Calendar.MONTH, 1);
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY, 5);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /**
     * Senka cut-off: last day of month at 14:00 JST.
     * "Last day" = last day of current calendar month.
     */
    private static long nextSenkaReset(Calendar now) {
        Calendar c = Calendar.getInstance(JST);
        int year  = now.get(Calendar.YEAR);
        int month = now.get(Calendar.MONTH);

        int lastDay = getLastDayOfMonth(year, month);
        c.set(year, month, lastDay, 14, 0, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() > now.getTimeInMillis()) {
            return c.getTimeInMillis();
        }
        // Next month's last day
        int nextMonth = month + 1;
        int nextYear  = year;
        if (nextMonth > Calendar.DECEMBER) {
            nextMonth = Calendar.JANUARY;
            nextYear++;
        }
        lastDay = getLastDayOfMonth(nextYear, nextMonth);
        c.set(nextYear, nextMonth, lastDay, 14, 0, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** EO reset: 1st of month 00:00 JST. */
    private static long nextEoReset(Calendar now) {
        Calendar c = Calendar.getInstance(JST);
        int year  = now.get(Calendar.YEAR);
        int month = now.get(Calendar.MONTH);

        c.set(year, month, 1, 0, 0, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() > now.getTimeInMillis()) {
            return c.getTimeInMillis();
        }
        c.add(Calendar.MONTH, 1);
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    // ---- helpers ----

    private static long buildJstEpoch(Calendar base, int addDays,
                                      int addMonths, int hour, int min, int sec) {
        Calendar c = (Calendar) base.clone();
        c.setTimeZone(JST);
        if (addMonths != 0) c.add(Calendar.MONTH, addMonths);
        if (addDays   != 0) c.add(Calendar.DAY_OF_YEAR, addDays);
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, min);
        c.set(Calendar.SECOND, sec);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static long buildJstEpochNextDay(Calendar base, int hour, int min, int sec) {
        return buildJstEpoch(base, 1, 0, hour, min, sec);
    }

    private static int getLastDayOfMonth(int year, int month) {
        Calendar c = Calendar.getInstance(JST);
        c.set(year, month, 1);
        return c.getActualMaximum(Calendar.DAY_OF_MONTH);
    }

    // ---- formatting ----

    /**
     * Format remaining milliseconds as a countdown string.
     * <1 day: HH:MM:SS
     * >=1 day: Xd HH:MM:SS
     */
    public static String formatCountdown(long remainMs) {
        if (remainMs <= 0) return "00:00:00";
        long totalSec = remainMs / 1000;
        long days  = totalSec / 86400;
        long hours = (totalSec % 86400) / 3600;
        long mins  = (totalSec % 3600) / 60;
        long secs  = totalSec % 60;
        if (days > 0) {
            return KcaUtils.format("%dd %02d:%02d:%02d", days, hours, mins, secs);
        }
        return KcaUtils.format("%02d:%02d:%02d", hours, mins, secs);
    }
}
