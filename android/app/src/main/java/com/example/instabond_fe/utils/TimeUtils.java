package com.example.instabond_fe.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class TimeUtils {
    public static String getRelativeTime(String createdAt) {
        Date date = parseInstant(createdAt);
        if (date == null) {
            return "Gan day";
        }

        long diff = safeDiffFromNow(date);
        if (diff < 60000) {
            return "Vua xong";
        } else if (diff < 3600000) {
            long minutes = Math.max(1, diff / 60000);
            return minutes + " phut truoc";
        } else if (diff < 86400000) {
            long hours = Math.max(1, diff / 3600000);
            return hours + " gio truoc";
        } else if (diff < 2592000000L) {
            long days = Math.max(1, diff / 86400000);
            return days + " ngay truoc";
        }

        SimpleDateFormat outFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        return outFormat.format(date);
    }

    public static String getCompactRelativeTime(String createdAt) {
        Date date = parseInstant(createdAt);
        if (date == null) {
            return "JUST NOW";
        }

        long diff = safeDiffFromNow(date);
        if (diff < 60000) {
            return "JUST NOW";
        } else if (diff < 3600000) {
            long minutes = Math.max(1, diff / 60000);
            return minutes + "M AGO";
        } else if (diff < 86400000) {
            long hours = Math.max(1, diff / 3600000);
            return hours + "H AGO";
        } else if (diff < 2592000000L) {
            long days = Math.max(1, diff / 86400000);
            return days + "D AGO";
        }

        SimpleDateFormat outFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        return outFormat.format(date).toUpperCase(Locale.getDefault());
    }

    public static String getConversationTimeLabel(String createdAt) {
        Date date = parseInstant(createdAt);
        if (date == null) {
            return "";
        }

        long diff = safeDiffFromNow(date);
        if (diff < 60000) {
            return "now";
        } else if (diff < 3600000) {
            long minutes = Math.max(1, diff / 60000);
            return minutes + "m ago";
        } else if (diff < 86400000) {
            long hours = Math.max(1, diff / 3600000);
            return hours + "h ago";
        } else if (diff < 172800000L) {
            long days = Math.max(1, diff / 86400000);
            return days + "d ago";
        }

        SimpleDateFormat outFormat = new SimpleDateFormat("dd/MM", Locale.getDefault());
        return outFormat.format(date);
    }

    public static String getChatClockLabel(String createdAt) {
        Date date = parseInstant(createdAt);
        if (date == null) {
            return "";
        }

        SimpleDateFormat outFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        return outFormat.format(date).toUpperCase(Locale.getDefault());
    }

    private static Date parseInstant(String createdAt) {
        if (createdAt == null || createdAt.isEmpty()) {
            return null;
        }

        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

            String timeStr = createdAt;
            if (timeStr.contains(".")) {
                timeStr = timeStr.substring(0, timeStr.indexOf("."));
            }
            if (timeStr.endsWith("Z")) {
                timeStr = timeStr.substring(0, timeStr.length() - 1);
            }

            return sdf.parse(timeStr);
        } catch (ParseException e) {
            return null;
        }
    }

    private static long safeDiffFromNow(Date date) {
        return Math.max(System.currentTimeMillis() - date.getTime(), 0L);
    }
}
