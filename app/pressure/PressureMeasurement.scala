package pressure

import org.joda.time.DateTime
import play.api.libs.json.JsValue
import org.joda.time.format.ISODateTimeFormat
import play.api.libs.json.Json
import play.api.libs.json.JsObject
import temperature.TemperatureMeasurement
import controllers.Measurement
import controllers.MeasurementSource

case class PressureMeasurement(id: Int, date: DateTime, deviceId: String, pa: Int) {
  def toJson : JsObject = 
    Json.obj("date" -> TemperatureMeasurement.dateFormat.print(date),
             "deviceId" -> deviceId,
             "Pa" -> pa)
  def toMeasurement = Measurement(date, deviceId, pa, MeasurementSource.PRESSURE)
}

object PressureMeasurement {
  def apply(json: JsValue): PressureMeasurement = {
    val dateString = (json \ "date").as[String]
    val date = TemperatureMeasurement.dateFormat.parseDateTime(dateString)
    val deviceId = (json \ "deviceId").as[String]
    val milliC = (json \ "milliC").as[Int]
    PressureMeasurement(-1, date, deviceId, milliC)
  }
  
  def findDailyMinimums(data: Seq[PressureMeasurement]): Seq[(DateTime, PressureMeasurement)] =
    findDailySomething(data, (dailyData) => dailyData.minBy { tm => tm.pa })

  def findDailyMaximums(data: Seq[PressureMeasurement]): Seq[(DateTime, PressureMeasurement)] =
    findDailySomething(data, (dailyData) => dailyData.maxBy { tm => tm.pa })

  private def findDailySomething(data: Seq[PressureMeasurement], 
                         selector: (Seq[PressureMeasurement]) => PressureMeasurement): Seq[(DateTime, PressureMeasurement)] = 
  {
    val groupedDaily = data.groupBy { tm => tm.date.withMillisOfDay(0) }.toList
    groupedDaily.map{case (d, dailyData) =>
      (d, selector(dailyData))
    }
  }
}

