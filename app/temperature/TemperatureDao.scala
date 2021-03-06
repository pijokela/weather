package temperature

import com.google.inject.Inject
import slick.driver.JdbcProfile
import play.api.db.slick.DatabaseConfigProvider
import slick.driver.PostgresDriver.api._
import slick.lifted.ProvenShape
import org.joda.time.DateTime
import java.sql.Timestamp
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import com.google.inject.ImplementedBy
import play.api.Logger
import slick.lifted.ProvenShape.proveShapeOf
import controllers.TimeSlots._
import controllers.Measurement

@ImplementedBy(classOf[TemperaturePostgresDao])
trait TemperatureDao {
  def store(measurement: Measurement): Future[Int]
  def store(measurements: List[TemperatureMeasurement]): Future[List[Int]]
  def store(date: DateTime, deviceId: String, milliC: Int): Future[Int]
  def deleteDeviceMeasurements(deviceId: String): Future[Int]
  def list(resultCount: Int = 20): Future[Seq[TemperatureMeasurement]]
  def list(time: String, now: DateTime): Future[Seq[TemperatureMeasurement]]
  def listToday: Future[Seq[TemperatureMeasurement]]
}

class TemperaturePostgresDao @Inject()(protected val dbConfigProvider: DatabaseConfigProvider) extends TemperatureDao {
  val dbConfig = dbConfigProvider.get[JdbcProfile]
  val db = dbConfig.db

  val measurements: TableQuery[TemperatureMeasurementDb] = TableQuery[TemperatureMeasurementDb]
  
  override def store(measurements: List[TemperatureMeasurement]) : Future[List[Int]] = {
    Future.sequence(measurements.map(m => store(m.date, m.deviceId, m.milliC)))
  }
  
  override def store(measurement: Measurement) : Future[Int] =
    store(measurement.date, measurement.deviceId, measurement.value)
  
  override def store(date: DateTime, deviceId: String, milliC: Int) : Future[Int] = {
    db.run(
      (measurements returning measurements.map(_.id)) += (0, new Timestamp(date.getMillis), deviceId, milliC)
    )
  }
  
  override def deleteDeviceMeasurements(deviceId: String): Future[Int] = {
    db.run(
      measurements.filter(_.device === deviceId).delete
    )
  }
  
  override def listToday: Future[Seq[TemperatureMeasurement]] = {
    val startOfDay = startOfToday
    
    db.run(measurements.filter(_.date > startOfDay).result).map { tmDbSeq => 
      tmDbSeq.map { case (id, ts, deviceId, milliC) => 
        val dt = new DateTime(ts.getTime)
        TemperatureMeasurement(id, dt, deviceId, milliC)
      }
    }
  }
  
  override def list(time: String, now: DateTime): Future[Seq[TemperatureMeasurement]] = {
    val (start, end) = getStartAndEnd(time, now)
    
    db.run(measurements.filter(_.date > ts(start)).filter(_.date < ts(end)).result).map { tmDbSeq => 
      tmDbSeq.map { case (id, ts, deviceId, milliC) => 
        val dt = new DateTime(ts.getTime)
        TemperatureMeasurement(id, dt, deviceId, milliC)
      }
    }
  }
  
  override def list(resultCount: Int) : Future[Seq[TemperatureMeasurement]] = {
    db.run(measurements.take(resultCount).result).map { tmDbSeq => 
      tmDbSeq.map { case (id, ts, deviceId, milliC) => 
        val dt = new DateTime(ts.getTime)
        TemperatureMeasurement(id, dt, deviceId, milliC)
      }
    }
  }
}

// A Suppliers table with 6 columns: id, name, street, city, state, zip
class TemperatureMeasurementDb(tag: Tag)
  extends Table[(Int, Timestamp, String, Int)](tag, "temperature_measurements") {

  // This is the primary key column:
  def id: Rep[Int] = column[Int]("id", O.AutoInc, O.PrimaryKey)
  def date: Rep[Timestamp] = column[Timestamp]("date")
  def device: Rep[String] = column[String]("device_id")
  def temperatureMilliC: Rep[Int] = column[Int]("temperature_millic")
  
  // Every table needs a * projection with the same type as the table's type parameter
  def * : ProvenShape[(Int, Timestamp, String, Int)] =
    (id, date, device, temperatureMilliC)
}

