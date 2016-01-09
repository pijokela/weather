package jchart

import temperature.TemperatureMeasurement
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.libs.json.JsArray
import play.api.libs.json.JsString
import play.api.libs.json.JsNumber
import org.joda.time.DateTime
import temperature.TemperatureMeasurement
import play.api.Logger
import java.util.Random
import play.api.Configuration
import com.google.inject.Inject
import scala.annotation.tailrec
import controllers.Config

class ChartData @Inject()(val configuration: Config) {
  
  val selectGroupingForTime: Map[String, Grouping] = Map(
      "rollingWeek" -> Daily4Grouping,
      "rolling30days" -> DailyGrouping
    ).withDefaultValue(HourlyGrouping)
  
  private def createNoGroups(data : Seq[TemperatureMeasurement]): Seq[DateTime] = {
    data.map(_.date)
  }
  
  def dailyMinsAndMaxes(start: DateTime, end: DateTime, data : Seq[(String, Seq[TemperatureMeasurement])]): JsObject = {
    val labels = Labels.forTimeAndGrouping(DailyGrouping, start, end)
    Logger.info(s"Finding mins and maxes $labels from data.size: ${data.size} and data.head._2.size: ${data.head._2.size}")
    val groupedData = data.flatMap { case (deviceId, data) => 
      (deviceId + ".max", TemperatureMeasurement.findDailyMaximums(data).map(_._2)) ::
      (deviceId + ".min", TemperatureMeasurement.findDailyMinimums(data).map(_._2)) :: Nil
    }
    // Find dates that have data:
    val dataDates = groupedData.flatMap(pair => pair._2.map(_.date)).groupBy(_.withMillisOfDay(0)).keys.toSet
    // Filter labels that do not have data in the first dataset:
    val labelsWithData = labels.filter { l => dataDates.contains(l.date) }
    
    Logger.info(s"Found ${labelsWithData.size} labels and ${groupedData.head._2.size} data points.")
    createJsonFromDataByDevice(labelsWithData.map(_.label), groupedData)
  }
  
  def fromMeasurements(start: DateTime, 
                       end: DateTime, 
                       data : Seq[(String, Seq[TemperatureMeasurement])], 
                       grouping: Grouping): JsObject = 
  {
    val labels = Labels.forTimeAndGrouping(grouping, start, end)
    Logger.info(s"Grouping is ${grouping.name} --> $labels from data.size: ${data.size}")
    val groupedData = data.flatMap { case (deviceId, data) => 
      Labels.findDataFor(labels, data)
    }
    createJsonFromData(labels.map(_.label), groupedData)
  }
  
  private val colors = Vector(/*(151, 187, 205),(151, 205, 187),(187, 151, 205),(187, 205, 151),*/(33, 140, 141),(108, 206, 203), (249, 229, 89), ( 239, 113, 38), (142, 220, 157), (71, 62, 63),(205, 151, 187))
  private val rand = new Random(System.currentTimeMillis())
  private val number = Math.abs(rand.nextInt())
  private def color(index: Int, opacity: Double): String = {
    val tuple = colors(index % colors.length)
    s"rgba(${tuple._1},${tuple._2},${tuple._3},$opacity)"
  }

  private def createJsonFromData(labelList : List[String], data : Seq[TemperatureMeasurement]): JsObject = {
    val dataByDevice = data.groupBy { m => m.deviceId }.toList
    createJsonFromDataByDevice(labelList, dataByDevice)
  }
  
  /**
   * Push in a list of measurements and get a JSON document
   * you can send to the web page.
   */
  private def createJsonFromDataByDevice(labelList : List[String], dataByDevice : Seq[(String, Seq[TemperatureMeasurement])]): JsObject = {
    var index = 0;
    def nextIndex() : Unit = {
      index = index + 1
    }
    
    val meanLabelDataList = dataByDevice.map { case (deviceId, measurements) => 
      
      val temperatures = measurements.sortWith((d1,d2) => d1.date.isBefore(d2.date)).map(_.milliC / 1000.0)
      val meanValue = temperatures.sum / temperatures.length.asInstanceOf[Double]
      val datasetDataArray = JsArray(temperatures.map(JsNumber(_)))
      
      val deviceLabel = configuration.string(s"deviceId.$deviceId.label").getOrElse(deviceId)
      val json = Json.obj(
        "label" -> deviceLabel,
        "fillColor" -> color(index, 0.2),
        "strokeColor" -> color(index, 1),
        "pointColor" -> color(index, 1),
        "pointStrokeColor" -> "#fff",
        "pointHighlightFill" -> "#fff",
        "pointHighlightStroke" -> color(index, 1),
        "data" -> datasetDataArray
      )
      nextIndex()
      (meanValue, deviceLabel, json)
    }.sortWith(_._1 > _._1)
    
    val labels = JsArray(labelList.map { s => JsString(s) })
    
    val datasets = JsArray(meanLabelDataList.map(_._3))
    Json.obj("labels" -> labels, "datasets" -> datasets)
  }
}

trait Grouping {
  /**
   * A descriptive name of the grouping. Used as URL parameter.
   */
  def name: String
  
  /**
   * Return the next labelled time after the time given as parameter.
   */
  def timeAfter(pointInTime: DateTime): DateTime
}

case object HourlyGrouping extends Grouping {
  override val name: String = "hourly"
  override def timeAfter(pointInTime: DateTime): DateTime = 
    pointInTime.withMinuteOfHour(0).withSecondOfMinute(0).withMillisOfSecond(0).plusHours(1)
}

case object NoGrouping extends Grouping {
  override val name: String = "none"
  private val minutes = 10 :: 20 :: 30 :: 40 :: 50 :: Nil
  override def timeAfter(pointInTime: DateTime): DateTime = {
    val mins = pointInTime.getMinuteOfHour
    pointInTime.withMinuteOfHour(0).plusMinutes(minutes.find { m => m > mins }.getOrElse(60)).withSecondOfMinute(0).withMillisOfSecond(0)
  }
}

case object Daily4Grouping extends Grouping {
  override val name: String = "4daily"
  private val hours = 3 :: 9 :: 15 :: 21 :: Nil
  override def timeAfter(pointInTime: DateTime): DateTime = {
    val hrs = pointInTime.getHourOfDay
    val nextHour = hours.find { h => h > hrs }.getOrElse(27)
    pointInTime.withHourOfDay(0).plusHours(nextHour).withMinuteOfHour(0).withSecondOfMinute(0).withMillisOfSecond(0)
  }
}

case object DailyGrouping extends Grouping {
  override val name: String = "daily"
  override def timeAfter(pointInTime: DateTime): DateTime = {
    val hrs = pointInTime.getHourOfDay
    pointInTime.withMillisOfDay(0).plusDays(1)
  }
}

case class Label(label: String, pointInTime: DateTime) {
  def date : DateTime = pointInTime.withMillisOfDay(0)
}

object Labels {
  
  @tailrec
  def findDataFor(labels: List[Label], data: Seq[TemperatureMeasurement], result: List[TemperatureMeasurement] = Nil): List[TemperatureMeasurement] = {
    Logger.info("Finding from data: " + data.size)
    labels.headOption match {
      case None => result
      case Some(Label(_, time)) => {
        val nextAndRest = data.dropWhile { d => d.date.isBefore(time) }
        Logger.info("Dropping to: " + nextAndRest.size)
        if (nextAndRest.isEmpty)
          result
        else 
          findDataFor(labels.tail, nextAndRest.tail, nextAndRest.head :: result)
      }
    }
  }
  
  def forTimeAndGrouping(grouping: Grouping, start: DateTime, end: DateTime): List[Label] = {
    Logger.info("Starting to find labels: " + grouping + " start: " + start + " end: " + end)
    @tailrec
    def groupTimes(from: DateTime, toList: List[DateTime]): List[DateTime] = if (from.isEqual(end) || from.isAfter(end)) {
      toList
    } else {
      Logger.info("Finding labels: " + from + " --> " + toList.size)
      groupTimes(grouping.timeAfter(from), from :: toList)
    }
    
    val times = groupTimes(grouping.timeAfter(start.minusSeconds(1)), Nil)
      .sortWith((t1, t2) => t1.isBefore(t2))
      
    val yearChanges  = times.headOption.map(_.getYear) != times.reverse.headOption.map(_.getYear)
    
    // zip with previous:
    val timesWithPrev = times zip None :: times.map(Some(_))
      
    timesWithPrev
      .map { case (t, prev) =>
        
        val tstr = (t, prev) match {
          case (t, _) if (grouping == DailyGrouping && yearChanges) => "d.M.yyyy"
          case (t, _) if (grouping == DailyGrouping) => "d.M"
          case (t, None) if (yearChanges)  => "d.M.yyyy - HH'h'"
          case (t, None) => "d.M - HH'h'"
          case (t, Some(p)) if (yearChanges && t.getDayOfMonth != p.getDayOfMonth) => "d.M.yyyy - HH'h'"
          case (t, Some(p)) if (t.getDayOfMonth != p.getDayOfMonth) => "d.M - HH'h'"
          case (t, _) => "HH'h'"
        }
        
        Label(t.toString(tstr), t)
      }
  }
}