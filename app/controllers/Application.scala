package controllers

import play.api._
import play.api.mvc._
import com.google.inject.Inject
import temperature.TemperatureDao
import play.api.libs.concurrent.Execution.Implicits._
import java.io.File
import com.typesafe.config.ConfigException.Missing
import w1reader.W1Temperatures
import org.joda.time.DateTime
import scala.concurrent.Future
import play.api.libs.json._
import jchart.ChartData
import jchart.ChartData
import temperature.TemperatureDao
import temperature.TemperatureMeasurement
import w1reader.W1Service
import pressure.PressureDao
import play.api.libs.ws.WS
import play.api.libs.ws.WSClient
import i2creader.I2cService

class Application @Inject()(
    val temperatureDao: TemperatureDao, 
    val pressureDao: PressureDao,
    val config: Config, 
    val w1service: W1Service,
    val i2cService: I2cService,
    val chartData: ChartData,
    val ws: WSClient) extends Controller 
{

  val storeDataLocally = config.boolean("application.mode.store-locally").getOrElse(false)
  val sendDataTo = config.stringList("application.mode.send-to")
  
  val log = Logger("application")
  val measurementSources : List[MeasurementSource] = List(w1service, i2cService)
  val jsonDatePattern = "yyyy-MM-dd'T'HH:mm:ss"
  
  def index = Action { 
    Ok(views.html.index())
  }
  
  def test = Action.async { request =>
    temperatureDao.list(20).map { temperatures => 
      val json = temperatures.map { t => Json.obj(
        "date" -> JsString(t.date.toString(jsonDatePattern)),
        "deviceId" -> JsString(t.deviceId),
        "temperature" -> JsNumber(t.milliC / 1000.0)
      ) }
      Ok(JsArray(json))
    }
  }
  
  def data(time: Option[String], grouping: Option[String]) = Action.async { request =>
    val validTime = time.getOrElse(TimeSlots.times.head)
    val validGrouping = chartData.selectGroupingForTime(validTime)
    Logger.info("Got time: " + time + " --> " + validTime)
    
    val now = DateTime.now()
    val (start, end) = TimeSlots.getStartAndEnd(validTime, now)
    Logger.info("Got start and end: " + start + " " + end)
    
    temperatureDao.list(validTime, now).map { temperatures => 
      Logger.info("Got data: " + temperatures.size)
      val groupedTemps = temperatures.groupBy { t => t.deviceId }.toList
      val data = validTime match {
        case "rolling30days" => chartData.dailyMinsAndMaxes(start, end, groupedTemps)
        case _ => chartData.fromMeasurements(start, end, groupedTemps, validGrouping)
      }
      Ok(data)
    }
  }
  
  /**
   * Receive measurements over HTTP POST
   */
  def postData() = Action.async { request =>
    val optionalJson = request.body.asJson
    if (optionalJson.isDefined) {
      val json = optionalJson.get
      val data = json.\("data").asOpt[JsArray]
      val dataList = data.toList.flatMap { d => d.value }
      
      if (dataList.isEmpty) {
        badRequestResponse("The JSON data must contain a field 'data' with an array of the measurements to store.")
      } else {
        val measurements = dataList.map(_.as[JsObject]).map(json => Measurement(json))
        
        val idListF = measurements.map { measurement =>
          measurement match {
            case m @ Measurement(_, _, _, MeasurementSource.TEMPERATURE) => 
              temperatureDao.store(m)
            case m @ Measurement(_, _, _, MeasurementSource.PRESSURE) => 
              pressureDao.store(m)
          }
        }
        
        Future.sequence(idListF).map{ idList =>
          Ok(Json.obj("meta" -> Json.obj(
              "storedMeasurements" -> idList.size
          )))
        }
      }
    } else {
      badRequestResponse("The POST body must contain a JSON object.")
    }
  }
  
  /**
   * Send 400 response with a JSON payload and message.
   */
  private def badRequestResponse(msg: String, storedMeasurements: Int = 0): Future[Result] = {
    Future.successful(
      BadRequest(Json.obj(
        "meta" -> Json.obj(
          "storedMeasurements" -> storedMeasurements,
          "message" -> msg
        )
      ))
    )
  }
  
  // # DELETE data from a specific device ID:
  // DELETE  /data           controllers.Application.deleteData(deviceId: String)
  
  def deleteData(deviceId: String) = Action.async { request =>
    val deletedRowsF = temperatureDao.deleteDeviceMeasurements(deviceId)
    deletedRowsF.map { deletedRows =>
      Ok(Json.obj(
        "meta" -> Json.obj(
          "deletedMeasurements" -> deletedRows,
          "deletedDeviceId" -> deviceId
        )
      ))
    }
  }
  
  def measure = Action.async { request =>
    
    val now = new DateTime
    val resultFutures = measurementSources.filter(_.isOnline()).map { ms => ms.measure(now) }
    val resultsFuture = Future.sequence(resultFutures)
    
    val rowsFuture : Future[List[Int]]= resultsFuture.flatMap { results =>
      val resultList = results.flatten
      
      // POST data to recipients:
      sendDataTo.map { recipient => 
        val jsonList = resultList.map(_.toJson)
        val json = Json.obj(
          "data" -> JsArray(jsonList)
        )
        ws.url(recipient + "/data").post(json).onFailure { case r => log.warn("Failed to send results: " + r.getMessage, r) }
      }
      
      if (storeDataLocally) {
        log.info("Storing measurements locally")
        // Store data to database:
        val rowsFuture = resultList.map { m => 
          m match {
            case Measurement(d,id,v,MeasurementSource.TEMPERATURE) => {
              log.info("Temperature measurement handled: " + v + " mC")
              temperatureDao.store(d, id, v)
            }
            case Measurement(d,id,v,MeasurementSource.PRESSURE) => {
              log.info("Pressure measurement handled: " + v + " Pa")
              pressureDao.store(d, id, v)
            }
          }
        }
        Future.sequence(rowsFuture)
      } else {
        log.info("Not storing measurements locally")
        Future.successful(List(0))
      }
    }
    
    rowsFuture.map { rows =>
      Ok(Json.obj(
        "meta" -> Json.obj(
          "temperatureRows" -> rows,
          "pressureRows" -> 0
        )
      ))
    }
  }

}
