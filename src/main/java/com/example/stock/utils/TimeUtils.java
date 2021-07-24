package com.example.stock.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import javafx.util.Pair;

/**
 * @author weibang
 * 2021/07/17
 */
public class TimeUtils {
    public static Long getTimeStamp(String dateTime) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try {
            Date date = sdf.parse(dateTime);
            return date.getTime() / 1000;
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return System.currentTimeMillis() / 1000;
    }

    public static Pair<String, String> getReportRatingTimeRangeStr(String dateTime, String format) {
        String[] times = dateTime.split(" - ");
        String fromStr = times[0];
        String toStr = times[1];
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        SimpleDateFormat sdf2 = new SimpleDateFormat(format);
        try {
            return new Pair<>(sdf2.format(sdf.parse(fromStr)), sdf2.format(sdf.parse(toStr)));
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return new Pair<>(null, null);
    }

    public static Pair<Long, Long> getTimeRange(String time) {
        String[] times = time.split(" - ");
        String fromStr = times[0];
        String toStr = times[1];
        Long from = TimeUtils.getTimeStamp(fromStr);
        Long to = TimeUtils.getTimeStamp(toStr);
        return new Pair<>(from, to);
    }
}
