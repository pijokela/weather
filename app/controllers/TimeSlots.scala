package controllers

import org.joda.time.DateTime
import play.api.Logger
import java.sql.Timestamp

/**
 * @author pirkka
 */
object TimeSlots {
  
  val times = "previous24h" :: "yesterday" :: "rollingWeek" :: "rolling30days" :: Nil
  
  def getStartAndEnd(time: String, now: DateTime): (DateTime, DateTime) = {
    time match {
      case "previous24h" => (now.minusDays(1), now)
      case "yesterday" => {
        val startOfToday = now.withMillisOfDay(0)
        (startOfToday.minusDays(1), startOfToday)
      }
      case "rollingWeek" => (now.minusWeeks(1), now)
      case "rolling30days" => (now.minusDays(30), now)
      case _ => {
        Logger.info("Unknown time: " + time) 
        (now.minusDays(1), now)
      }
    }
  }
  
  def startOfToday: Timestamp = {
    val now = DateTime.now()
    new Timestamp(now.withMillisOfDay(0).getMillis)
  }

  def ts(dateTime: DateTime): Timestamp = new Timestamp(dateTime.getMillis)
}