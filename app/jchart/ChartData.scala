package jchart

import dao.TemperatureMeasurement
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.libs.json.JsArray
import play.api.libs.json.JsString
import play.api.libs.json.JsNumber
import org.joda.time.DateTime
import dao.TemperatureMeasurement
import play.api.Logger
import java.util.Random
import play.api.Configuration
import com.google.inject.Inject

class ChartData @Inject()(val configuration: Configuration) {
  
  val groupings = "hourly" :: "none" :: Nil
  
  val selectGroupingForTime: Map[String, String] = Map(
      "rollingWeek" -> "4daily"
    ).withDefaultValue("hourly")
  
  val groupingFunctions: Map[String, (Seq[TemperatureMeasurement])=>Seq[DateTime]] = Map(
      "hourly" -> createHourGroups,
      "4daily" -> create4DailyGroups,
      "daily" -> createDailyGroups,
      "none" -> createNoGroups
    )
  
  private def createHourGroups(data : Seq[TemperatureMeasurement]): Seq[DateTime] = {
    val allDates = data.map(_.date).toSet
    val hourGroups = allDates.groupBy(_.toString("yyyy-MM-dd'T'HH")).values
    hourGroups.map { dateTimes =>
      dateTimes.minBy(_.getMillis)
    }.toSeq
  }
  
  private def createDailyGroups(data : Seq[TemperatureMeasurement]): Seq[DateTime] = {
    val allDates = data.map(_.date).toSet
    val hourGroups = allDates.groupBy(_.toString("yyyy-MM-dd")).values
    hourGroups.map { dateTimes =>
      dateTimes.minBy(_.getMillis)
    }.toSeq
  }
  
  private def create4DailyGroups(data : Seq[TemperatureMeasurement]): Seq[DateTime] = {
    def last2Letters(s: String): String = {
      s.substring(s.length() - 2, s.length())
    }
    val selectedHours = "03" :: "09" :: "15" :: "21" :: Nil
    
    val allDates = data.map(_.date).toSet
    val hourGroups = allDates
      .groupBy(_.toString("yyyy-MM-dd'T'HH"))
      .filter { case (k, v) => selectedHours.contains(last2Letters(k)) }
      .values
    
    hourGroups.map { dateTimes =>
      dateTimes.minBy(_.getMillis)
    }.toSeq
  }
  
  private def createNoGroups(data : Seq[TemperatureMeasurement]): Seq[DateTime] = {
    data.map(_.date)
  }
  
  def fromMeasurements(data : Seq[TemperatureMeasurement], grouping: String): JsObject = {
    val groups = groupingFunctions(grouping)(data)
    Logger.info(s"Grouping is $grouping --> $groups from data.size: ${data.size}")
    fromMeasurements(data.filter(m=>groups.contains(m.date)))
  }
  
  private val colors = Vector(/*(151, 187, 205),(151, 205, 187),(187, 151, 205),(187, 205, 151),*/(33, 140, 141),(108, 206, 203), (249, 229, 89), ( 239, 113, 38), (142, 220, 157), (71, 62, 63),(205, 151, 187))
  private val rand = new Random(System.currentTimeMillis())
  private val number = Math.abs(rand.nextInt())
  private def color(index: Int, opacity: Double): String = {
    val tuple = colors(index % colors.length)
    s"rgba(${tuple._1},${tuple._2},${tuple._3},$opacity)"
  }
  
  /**
   * Push in a list of measurements and get a JSON document
   * you can send to the web page.
   */
  def fromMeasurements(data : Seq[TemperatureMeasurement]): JsObject = {
    
    val labelList = data
      .map { m => m.date.toString("yyyy-MM-dd'T'HH-mm-ss") }
      .toSet.toList
      .sorted
      .map { l => JsString(l) }
    
    val labels = JsArray(labelList)
    
    var index = 0;
    def nextIndex() : Unit = {
      index = index + 1
    }
    
    val dataByDevice = data.groupBy { m => m.deviceId }.toList
    val datasetList = dataByDevice.map { case (deviceId, measurements) => 
      
      val temperatures = measurements.sortWith((d1,d2) => d1.date.isBefore(d2.date)).map(_.milliC / 1000.0)
      val datasetDataArray = JsArray(temperatures.map(JsNumber(_)))
      
      val deviceLabel = configuration.getString(s"deviceId.$deviceId.label").getOrElse(deviceId)
      val json = Json.obj(
        "label" -> deviceLabel,
        "fillColor" -> color(index, 0.4),
        "strokeColor" -> color(index, 1),
        "pointColor" -> color(index, 1),
        "pointStrokeColor" -> "#fff",
        "pointHighlightFill" -> "#fff",
        "pointHighlightStroke" -> color(index, 1),
        "data" -> datasetDataArray
      )
      nextIndex()
      json
    }
    
    val datasets = JsArray(datasetList)
    Json.obj("labels" -> labels, "datasets" -> datasets)
  }
}