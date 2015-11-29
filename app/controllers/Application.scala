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

class Application @Inject()(val temperatureDao: TemperatureDao, val configuration: Configuration) extends Controller {

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
  
  def data(grouping: Option[String]) = Action.async { request =>
    if (grouping.isDefined && !ChartData.groupings.contains(grouping.get))
      Future.successful(
        BadRequest(s"Grouping $grouping is not valid. Valid groupings are ${ChartData.groupings}")
      )
    else
      temperatureDao.listToday.map { temperatures => 
        val data = ChartData.fromMeasurements(temperatures, grouping)
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
