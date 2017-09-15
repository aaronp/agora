package agora.exec.dao

import java.time.LocalDate

/**
  * A time range, inclusive
  * @param from
  * @param to
  */
case class TimeRange(from: Timestamp, to: Timestamp) {

  require(!to.isBefore(from), s"Invalid range $from - $to")

  /** The time range completely contains the date/hr/minute if the 'from' date is before the
    * date/hr/minute and the 'to' date is the same or after the next minute after the date/hr/minute
    *
    * @param date   the date to check
    * @param hour   the hour of the date
    * @param minute the minute of the date
    * @return true if that date, hour and minute are within the time range
    */
  def completelyContainsDateHourAndMinute(date: LocalDate, hour: Int, minute: Int) = {
    val dateTimeInQuestion = date.atTime(hour, minute, 0, 0)

    !from.isAfter(dateTimeInQuestion) && !to.isBefore(dateTimeInQuestion.plusMinutes(1))
  }

  /** The time range completely contains the date/hr if the 'from' date is before the
    * date/hr and the 'to' date is the same or after the next mour after the date/hr
    *
    * @param date   the date to check
    * @param hour   the hour of the date
    * @return true if that date and hour are within the time range
    */
  def completelyContainsDateAndHour(date: LocalDate, hour: Int): Boolean = {
    val dateTimeInQuestion = date.atTime(hour, 0, 0, 0)

    !from.isAfter(dateTimeInQuestion) && !to.isBefore(dateTimeInQuestion.plusHours(1))
  }

  /**
    * @param date the date to check
    * @return true if the entire date is within this time range
    */
  def completelyContainsDate(date: LocalDate): Boolean = {
    (date isAfter from.toLocalDate) && (date isBefore to.toLocalDate)
  }

  def contains(time: Timestamp) = !time.isBefore(from) && !time.isAfter(to)

  def contains(date: LocalDate): Boolean = {
    !date.isBefore(from.toLocalDate) && !date.isAfter(to.toLocalDate)
  }

}