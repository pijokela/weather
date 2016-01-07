package w1reader

import java.io.File
import scala.io.Source
import scala.concurrent.Future
import controllers.Config
import com.google.inject.Inject
import com.typesafe.config.ConfigException.Missing
import org.joda.time.DateTime
import controllers.Measurement
import controllers.MeasurementSource
import play.api.libs.concurrent.Execution.Implicits._

/**
 * Implement MeasurementSource interface to allow using w1 devices
 * in a standard way.
 */
class W1Service @Inject()(config: Config) extends MeasurementSource {
  val file = new File(config.requiredString("w1devices.dir"))

  override def isOnline = config.boolean("w1Service.online").getOrElse(false)
  
  override def measure(now: DateTime): Future[List[Measurement]] = {
    val reader = new W1Temperatures(file)
    val resultsFuture = reader.filenameAndTempList
    val now = new DateTime
    
    resultsFuture.map(_.map{case (id, milliC) => Measurement(now, id, milliC, MeasurementSource.TEMPERATURE)})
  }
}

/**
 * This class reads temperature values from one wire devices.
 */
class W1Temperatures(devicesDir: File) {
  private def deviceDirs : Seq[File] = Option(devicesDir.listFiles()).toList.flatten.filter { f => f.isDirectory() }
  
  private def temperatureDataFiles : Seq[File] = deviceDirs.map { d => 
    new File(d, "w1_slave") }.filter { f => f.exists() 
  }
  
  def filenameAndTempList : Future[List[(String, Int)]] = { 
    // In reality this needs a different thread pool.
    import play.api.libs.concurrent.Execution.Implicits._
    
    Future {
      temperatureDataFiles.map { w1file =>
        val source = Source.fromFile(w1file, "UTF-8")
        
        //38 00 4b 46 7f ff 08 10 25 : crc=25 YES
        //38 00 4b 46 7f ff 08 10 25 t=3500
        val dataLine = source.getLines().toList.tail.head
        val tempEquals = dataLine.split(" ").reverse.head
        
        // Temperature on mC
        val temp = tempEquals.substring(2).toInt
        
        (w1file.getParentFile.getName, temp)
      }.toList
    }
  }
}