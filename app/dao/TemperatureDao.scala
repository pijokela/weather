package dao

import com.google.inject.Inject
import slick.driver.JdbcProfile
import play.api.db.slick.DatabaseConfigProvider
import slick.driver.PostgresDriver.api._
import slick.lifted.{ProvenShape, ForeignKeyQuery}
import org.joda.time.DateTime
import java.sql.Timestamp
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future

import com.google.inject.ImplementedBy

@ImplementedBy(classOf[TemperaturePostgresDao])
trait TemperatureDao {
  def store(date: DateTime, deviceId: String, milliC: Int): Future[Int]
  def list: Future[Seq[TemperatureMeasurement]]
}

case class TemperatureMeasurement(id: Int, date: DateTime, deviceId: String, milliC: Int)

class TemperaturePostgresDao @Inject()(protected val dbConfigProvider: DatabaseConfigProvider) extends TemperatureDao {
  val dbConfig = dbConfigProvider.get[JdbcProfile]
  val db = dbConfig.db

  val measurements: TableQuery[TemperatureMeasurementDb] = TableQuery[TemperatureMeasurementDb]
  
  override def store(date: DateTime, deviceId: String, milliC: Int) : Future[Int] = {
    db.run(
      (measurements returning measurements.map(_.id)) += (0, new Timestamp(date.getMillis), deviceId, milliC)
    )
  }
  
  override def list : Future[Seq[TemperatureMeasurement]] = {
    db.run(measurements.result).map { tmDbSeq => 
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