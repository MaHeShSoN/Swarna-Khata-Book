package com.jewelrypos.swarnakhatabook.Utilitys


import java.util.Calendar
import java.util.Date

/**
 * Helper class for common date range operations used in reporting features
 */
class DateRangeHelper {
    companion object {
        /**
         * Get date range for today (start of day to now)
         */
        fun getTodayRange(): Pair<Date, Date> {
            val calendar = Calendar.getInstance()

            // End date is now
            val endDate = calendar.time

            // Start date is beginning of today
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startDate = calendar.time

            return Pair(startDate, endDate)
        }

        /**
         * Get date range for yesterday (start of yesterday to end of yesterday)
         */
        fun getYesterdayRange(): Pair<Date, Date> {
            val calendar = Calendar.getInstance()

            // Move to yesterday
            calendar.add(Calendar.DAY_OF_YEAR, -1)

            // End date is end of yesterday
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            calendar.set(Calendar.MILLISECOND, 999)
            val endDate = calendar.time

            // Start date is beginning of yesterday
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startDate = calendar.time

            return Pair(startDate, endDate)
        }

        /**
         * Get date range for this week (start of week to now)
         */
        fun getThisWeekRange(): Pair<Date, Date> {
            val calendar = Calendar.getInstance()

            // End date is now
            val endDate = calendar.time

            // Start date is beginning of this week (Sunday or Monday depending on locale)
            calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startDate = calendar.time

            return Pair(startDate, endDate)
        }

        /**
         * Get date range for this month (start of month to now)
         */
        fun getThisMonthRange(): Pair<Date, Date> {
            val calendar = Calendar.getInstance()

            // End date is now
            val endDate = calendar.time

            // Start date is beginning of this month
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startDate = calendar.time

            return Pair(startDate, endDate)
        }

        /**
         * Get date range for last month (start of last month to end of last month)
         */
        fun getLastMonthRange(): Pair<Date, Date> {
            val calendar = Calendar.getInstance()

            // Move to last month
            calendar.add(Calendar.MONTH, -1)

            // Start date is beginning of last month
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startDate = calendar.time

            // End date is end of last month
            calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            calendar.set(Calendar.MILLISECOND, 999)
            val endDate = calendar.time

            return Pair(startDate, endDate)
        }

        /**
         * Get date range for this quarter (start of quarter to now)
         */
        fun getThisQuarterRange(): Pair<Date, Date> {
            val calendar = Calendar.getInstance()

            // End date is now
            val endDate = calendar.time

            // Determine the first month of the current quarter
            val month = calendar.get(Calendar.MONTH)
            val quarterStartMonth = month - (month % 3)

            // Start date is beginning of the quarter
            calendar.set(Calendar.MONTH, quarterStartMonth)
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startDate = calendar.time

            return Pair(startDate, endDate)
        }

        /**
         * Get date range for this year (start of year to now)
         */
        fun getThisYearRange(): Pair<Date, Date> {
            val calendar = Calendar.getInstance()

            // End date is now
            val endDate = calendar.time

            // Start date is beginning of this year
            calendar.set(Calendar.MONTH, Calendar.JANUARY)
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startDate = calendar.time

            return Pair(startDate, endDate)
        }

        /**
         * Get date range for all time (Unix epoch to now)
         */
        fun getAllTimeRange(): Pair<Date, Date> {
            // End date is now
            val endDate = Calendar.getInstance().time

            // Start date is first day of 2010 (arbitrary "old" date that should cover all historic data)
            val calendar = Calendar.getInstance()
            calendar.set(2010, Calendar.JANUARY, 1, 0, 0, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startDate = calendar.time

            return Pair(startDate, endDate)
        }

        /**
         * Add end-of-day time components to a date
         */
        fun setEndOfDay(date: Date): Date {
            val calendar = Calendar.getInstance()
            calendar.time = date
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            calendar.set(Calendar.MILLISECOND, 999)
            return calendar.time
        }

        /**
         * Add start-of-day time components to a date
         */
        fun setStartOfDay(date: Date): Date {
            val calendar = Calendar.getInstance()
            calendar.time = date
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            return calendar.time
        }
    }
}