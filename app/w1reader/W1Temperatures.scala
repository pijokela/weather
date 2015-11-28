package w1reader

import java.io.File
import scala.io.Source
import scala.concurrent.Future

/**
 * This class reads temperature values from one wire devices.
 */
class W1Temperatures(devicesDir: File) {
  private def deviceDirs : Seq[File] = devicesDir.listFiles().filter { f => f.isDirectory() }
  
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