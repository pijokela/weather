package temperature

import org.joda.time.DateTime
import play.api.libs.json.JsValue
import org.joda.time.format.ISODateTimeFormat
import play.api.libs.json.Json
import play.api.libs.json.JsObject
import controllers.Measurement
import controllers.MeasurementSource

case class TemperatureMeasurement(id: Int, date: DateTime, deviceId: String, milliC: Int) {
  def toJson : JsObject = 
    Json.obj("date" -> TemperatureMeasurement.dateFormat.print(date),
             "deviceId" -> deviceId,
             "milliC" -> milliC)
  def toMeasurement = Measurement(date, deviceId, milliC, MeasurementSource.TEMPERATURE)
}

object TemperatureMeasurement {
  val dateFormat = ISODateTimeFormat.dateTimeNoMillis()
  
  def apply(json: JsValue): TemperatureMeasurement = {
    val dateString = (json \ "date").as[String]
    val date = dateFormat.parseDateTime(dateString)
    val deviceId = (json \ "deviceId").as[String]
    val milliC = (json \ "milliC").as[Int]
    TemperatureMeasurement(-1, date, deviceId, milliC)
  }
  
  def findDailyMinimums(data: Seq[TemperatureMeasurement]): Seq[(DateTime, TemperatureMeasurement)] =
    findDailySomething(data, (dailyData) => dailyData.minBy { tm => tm.milliC })

  def findDailyMaximums(data: Seq[TemperatureMeasurement]): Seq[(DateTime, TemperatureMeasurement)] =
    findDailySomething(data, (dailyData) => dailyData.maxBy { tm => tm.milliC })

  private def findDailySomething(data: Seq[TemperatureMeasurement], 
    selector: (Seq[TemperatureMeasurement]) => TemperatureMeasurement): Seq[(DateTime, TemperatureMeasurement)] = 
  {
    val groupedDaily = data.groupBy { tm => tm.date.withMillisOfDay(0) }.toList
    groupedDaily.map{case (d, dailyData) =>
      (d, selector(dailyData))
    }
  }
}

