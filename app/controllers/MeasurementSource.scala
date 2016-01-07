package controllers

import org.joda.time.DateTime
import scala.concurrent.Future
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import temperature.TemperatureMeasurement

object MeasurementSource {
  type Type = String
  val PRESSURE    = "pressure"
  val TEMPERATURE = "temperature"
}

/**
 * @author pirkka
 */
trait MeasurementSource {
  def isOnline(): Boolean
  def measure(now: DateTime): Future[List[Measurement]]
}

case class Measurement(date: DateTime, deviceId: String, value: Int, measurementType: MeasurementSource.Type) {
  
  def isType(anotherType: MeasurementSource.Type): Boolean = measurementType == anotherType
  
  def toJson : JsObject = {
    val part1 = Json.obj("date" -> TemperatureMeasurement.dateFormat.print(date),
             "deviceId" -> deviceId)
    val part2 = if (measurementType == MeasurementSource.TEMPERATURE) {
      Json.obj("milliC" -> value)
    } else if (measurementType == MeasurementSource.PRESSURE) {
      Json.obj("Pa" -> value)
    } else Json.obj()
    part1 ++ part2
  }
}

object Measurement {
  def apply(json: JsObject) : Measurement = {
    val fields = json.fields.toMap
    val date = TemperatureMeasurement.dateFormat.parseDateTime(fields("date").as[String])
    val deviceId = fields("deviceId").as[String]
    val milliC = fields.get("milliC").map(_.as[Int])
    val pa = fields.get("Pa").map(_.as[Int])
    val value = milliC.getOrElse(pa.get)
    val measurementType = if (milliC.isDefined) MeasurementSource.TEMPERATURE else MeasurementSource.PRESSURE
    
    Measurement(date, deviceId, value, measurementType)
  }
}