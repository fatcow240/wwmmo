package au.com.codeka.common;

import java.util.Locale;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;
import org.joda.time.Interval;
import org.joda.time.Period;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

public class TimeFormatter {
    private int mMaxDays;
    private int mMinSeconds;

    private TimeFormatter() {
        mMaxDays = 2;
        mMinSeconds = 15;
    }

    public static TimeFormatter create() {
        return new TimeFormatter();
    }

    public TimeFormatter withMaxDays(int maxDays) {
        mMaxDays = maxDays;
        return this;
    }
    public TimeFormatter withMinSeconds(int minSeconds) {
        mMinSeconds = minSeconds;
        return this;
    }

    public String format(float timeInHours) {
        if (timeInHours > 0.0f) {
            int hrs = (int) Math.floor(timeInHours);
            int mins = (int) Math.floor((timeInHours - hrs) * 60.0f);

            return format(new Period(hrs, mins, 0, 0));
        } else {
            return "???";
        }
    }

    /**
     * Formats the time between now and then (we assume then is chronologically before now) as
     * an "hrs/mins" string.
     */
    public String format(DateTime now, DateTime then) {
        if (now.isBefore(then)) {
            DateTime tmp = now;
            now = then;
            then = tmp;
        }

        Duration d = new Interval(then, now).toDuration();
        if (d.compareTo(Duration.standardSeconds(5)) <  0)
            d = Duration.standardSeconds(5);

        return format(d.toPeriod());
    }

    public String format(DateTime then) {
        DateTime now = DateTime.now(DateTimeZone.UTC);
        if (now.isBefore(then)) {
            return "just now";
        }
        Duration d = new Interval(then, now).toDuration();
        if (d.compareTo(Duration.standardSeconds(mMinSeconds)) <  0)
            d = Duration.standardSeconds(5);

        if (d.compareTo(Duration.standardDays(mMaxDays)) >= 0) {
            DateTimeFormatter dtf = DateTimeFormat.shortDate();
            dtf.withLocale(Locale.getDefault());
            return then.toString(dtf);
        }

        return format(d.toPeriod());
    }

    public String format(Duration duration) {
        return format(duration.toPeriod());
    }

    public String format(Period p) {
        int days = p.toStandardDays().getDays();
        if (days > 0) {
            int hours = p.getHours() - (days * 24);
            return String.format(Locale.ENGLISH, "%d day%s, %d hr%s",
                                 days, days == 1 ? "" : "s",
                                 hours, hours == 1 ? "" : "s");
        } else if (p.getHours() > 3) {
            return String.format(Locale.ENGLISH, "%d hrs",
                    p.getHours());
        } else if (p.getHours() > 0) {
            return String.format(Locale.ENGLISH, "%d hr%s, %d min%s",
                    p.getHours(), p.getHours() == 1 ? "" : "s",
                    p.getMinutes(), p.getMinutes() == 1 ? "" : "s");
        } else if (p.getMinutes() <= 0) {
            return "just now";
        } else {
            return String.format(Locale.ENGLISH, "%d min%s",
                    p.getMinutes(), p.getMinutes() == 1 ? "" : "s");
        }
    }
}
