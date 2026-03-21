package com.example.instabond_fe.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class TimeUtils {
    public static String getRelativeTime(String createdAt) {
        if (createdAt == null || createdAt.isEmpty()) {
            return "Gần đây";
        }
        try {
            // Spring Boot Instant default serialization format
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

            String timeStr = createdAt;
            if (timeStr.contains(".")) {
                timeStr = timeStr.substring(0, timeStr.indexOf("."));
            } else if (timeStr.endsWith("Z")) {
                timeStr = timeStr.substring(0, timeStr.length() - 1);
            }

            Date date = sdf.parse(timeStr);
            if (date == null)
                return "Gần đây";

            long time = date.getTime();
            long now = System.currentTimeMillis();
            long diff = now - time;

            if (diff < 0) {
                diff = 0; // Fix clock skew
            }

            if (diff < 60000) { // < 1 min
                return "Vừa xong";
            } else if (diff < 3600000) { // < 1 hour
                long minutes = diff / 60000;
                return minutes + " phút trước";
            } else if (diff < 86400000) { // < 1 day
                long hours = diff / 3600000;
                return hours + " giờ trước";
            } else if (diff < 2592000000L) { // < 30 days
                long days = diff / 86400000;
                return days + " ngày trước";
            } else {
                SimpleDateFormat outFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                return outFormat.format(date);
            }
        } catch (ParseException e) {
            e.printStackTrace();
            return "Gần đây";
        }
    }
}
