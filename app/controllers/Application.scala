package controllers

import play.api._
import play.api.mvc._
import com.google.inject.Inject
import dao.TemperatureDao
import play.api.libs.concurrent.Execution.Implicits._
import java.io.File
import com.typesafe.config.ConfigException.Missing
import w1reader.W1Temperatures
import org.joda.time.DateTime
import scala.concurrent.Future
import play.api.libs.json._
import jchart.ChartData
import jchart.ChartData
import dao.TemperatureDao

class Application @Inject()(val temperatureDao: TemperatureDao, val configuration: Configuration, val chartData: ChartData) extends Controller {

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
    val validTime = time.getOrElse(temperatureDao.times.head)
    val validGrouping = chartData.selectGroupingForTime(validTime)
    Logger.info("Got time: " + time + " --> " + validTime)
    temperatureDao.list(validTime).map { temperatures => 
      val data = chartData.fromMeasurements(temperatures, validGrouping)
      Ok(data)
    }
  }
  
  def measure = Action.async { request =>
    
    val file = new File(configuration.getString("w1devices.dir", None).getOrElse(throw new Missing("w1devices.dir")))
    val reader = new W1Temperatures(file)
    val resultsFuture = reader.filenameAndTempList
    val now = new DateTime
    
    val rowsFuture = resultsFuture.flatMap { results =>
      val rowsFuture = results.map { case (filename, milliC) => 
        temperatureDao.store(now, filename, milliC)
      }
      Future.sequence(rowsFuture)
    }
    
    rowsFuture.map { rows =>
      Ok("" + rows)
    }
  }

}
