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

/**
 * @author pirkka
 */
object ChartData {
  
  val groupings = "hourly" :: "none" :: Nil
  
  val groupingFunctions: Map[String, (Seq[TemperatureMeasurement])=>Seq[DateTime]] = Map(
      "hourly" -> createHourGroups,
      "none" -> createNoGroups
    )
  
  private def createHourGroups(data : Seq[TemperatureMeasurement]): Seq[DateTime] = {
    val allDates = data.map(_.date).toSet
    val hourGroups = allDates.groupBy(_.toString("yyyy-MM-dd'T'HH")).values
    hourGroups.map { dateTimes =>
      dateTimes.minBy(_.getMillis)
    }.toSeq
  }
  
  private def createNoGroups(data : Seq[TemperatureMeasurement]): Seq[DateTime] = {
    data.map(_.date)
  }
  
  def fromMeasurements(data : Seq[TemperatureMeasurement], grouping: Option[String]): JsObject = {
    val groups = groupingFunctions(grouping.getOrElse("none"))(data)
    Logger.info(s"Grouping is $grouping --> $groups from data.size: ${data.size}")
    fromMeasurements(data.filter(m=>groups.contains(m.date)))
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
    
    val dataByDevice = data.groupBy { m => m.deviceId }.toList
    val datasetList = dataByDevice.map { case (deviceId, measurements) => 
      
      val temperatures = measurements.sortWith((d1,d2) => d1.date.isBefore(d2.date)).map(_.milliC / 1000.0)
      val datasetDataArray = JsArray(temperatures.map(JsNumber(_)))
      
      Json.obj(
        "label" -> deviceId,
        "fillColor" -> "rgba(151,187,205,0.2)",
        "strokeColor" -> "rgba(151,187,205,1)",
        "pointColor" -> "rgba(151,187,205,1)",
        "pointStrokeColor" -> "#fff",
        "pointHighlightFill" -> "#fff",
        "pointHighlightStroke" -> "rgba(151,187,205,1)",
        "data" -> datasetDataArray
      )
    }
    
    val datasets = JsArray(datasetList)
    Json.obj("labels" -> labels, "datasets" -> datasets)
  }
}