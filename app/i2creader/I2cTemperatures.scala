package i2creader

import com.pi4j.io.i2c.I2CBus
import java.io.DataInputStream
import com.pi4j.io.i2c.I2CDevice
import com.pi4j.io.i2c.I2CFactory
import play.api.Logger
import java.io.IOException
import java.io.ByteArrayInputStream
import com.google.inject.Inject
import controllers.Config
import controllers.MeasurementSource
import org.joda.time.DateTime
import controllers.Measurement
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits._
import controllers.Measurement

class I2cService @Inject()(config: Config) extends MeasurementSource {
  override def isOnline = config.boolean("i2cService.online").getOrElse(false)
  
  override def measure(now: DateTime): Future[List[Measurement]] = {
    val sensor = new Bmp180Sensor(Bmp180Sensor.STANDARD)
    Future {
      val pm = sensor.readPressure()
      val tm = sensor.readTemp()
      
      Measurement(now, "bmp180", pm, MeasurementSource.PRESSURE) :: 
        Measurement(now, "bmp180", tm, MeasurementSource.TEMPERATURE) :: 
        Nil
    }
  }
}

/**
 * https://github.com/adafruit/Adafruit_Python_BMP/blob/master/Adafruit_BMP/BMP085.py
 * https://blogs.oracle.com/acaicedo/resource/RPi-HOL/Sensor.java
 */
object Bmp180Sensor {
  // Operating modes for the pressure measurement:
  val ULTRALOWPOWER     = 0
  val STANDARD          = 1
  val HIGHRES           = 2
  val ULTRAHIGHRES      = 3
  
  //Calibration Data
  val EEPROM_start: Int = 0xAA;
  val EEPROM_end: Int = 0xBF;

  //Raspberry Pi's I2C bus
  val i2cBus: Int = 1;
  // Device address 
  val address: Int = 0x77;

  // Temperature Control Register Data
  val controlRegister: Int = 0xF4;
  val callibrationBytes: Int = 22;
  // Temperature read address
  val tempAddr: Byte = 0xF6.asInstanceOf[Byte]
  val pressureAddr: Byte = 0xF6.asInstanceOf[Byte]
  // Read temperature command
  val getTempCmd: Byte = 0x2E.asInstanceOf[Byte];
  // Read pressure command
  val getPressureCmd: Byte = 0x34.asInstanceOf[Byte];
}

class Bmp180Sensor(val mode: Int = Bmp180Sensor.STANDARD) {

  val log = Logger("Bmp180Sensor")
    
  // Device object
  var bmp180: I2CDevice = try {
    
    val bus = I2CFactory.getInstance(I2CBus.BUS_1);
    log.info("Connected to bus OK!!!");

    //get device itself
    val bmp180 = bus.getDevice(Bmp180Sensor.address);
    log.info("Connected to device OK!!!");

    //Small delay before starting
    Thread.sleep(500);
    bmp180
  } catch {
    case ioe: IOException => 
      log.error("Exception: " + ioe.getMessage(), ioe)
      throw ioe
    case ie: InterruptedException => 
      log.error("Interrupted Exception: " + ie.getMessage(), ie)
      throw ie
  }
  
  // EEPROM registers - these represent calibration data
  case class Calibration(
      AC1: Short, 
      AC2: Short, 
      AC3: Short, 
      AC4: Int, 
      AC5: Int, 
      AC6: Int, 
      B1: Short, 
      B2: Short, 
      MB: Short, 
      MC: Short,
      MD: Short)
  val calibration = calibrate()
  import calibration._
    
  def calibrate(): Calibration = {
    try {
      val bytes = new Array[Byte](Bmp180Sensor.callibrationBytes);

      //read all callibration data into byte array
      val readTotal = bmp180.read(Bmp180Sensor.EEPROM_start, bytes, 0, Bmp180Sensor.callibrationBytes);
      if (readTotal != 22) {
          log.error("Error bytes read: " + readTotal);
      }
      
      val bmp180CaliIn = new DataInputStream(new ByteArrayInputStream(bytes));

      val calibration = Calibration(
        // Read each of the pairs of data as signed short
        AC1 = bmp180CaliIn.readShort(),
        AC2 = bmp180CaliIn.readShort(),
        AC3 = bmp180CaliIn.readShort(),
  
        // Unsigned short Values
        AC4 = bmp180CaliIn.readUnsignedShort(),
        AC5 = bmp180CaliIn.readUnsignedShort(),
        AC6 = bmp180CaliIn.readUnsignedShort(),
  
        //Signed sort values
        B1 = bmp180CaliIn.readShort(),
        B2 = bmp180CaliIn.readShort(),
        MB = bmp180CaliIn.readShort(),
        MC = bmp180CaliIn.readShort(),
        MD = bmp180CaliIn.readShort()
      )
      
      log.debug("Callibration: " + calibration);
      
      calibration
    } catch {
      case e: IOException => 
        log.error("Exception: " + e.getMessage(), e)
        throw e
    }
  }

  /**
   * @return Uncompensated Temperature data
   */
  private def readRawTemp(): Int = {
    try {
      val bytesTemp = new Array[Byte](2);
      
      bmp180.write(Bmp180Sensor.controlRegister, Bmp180Sensor.getTempCmd);
      Thread.sleep(500);
      
      val readTotal = bmp180.read(Bmp180Sensor.tempAddr, bytesTemp, 0, 2);
      if (readTotal < 2) {
        log.error(s"Error: $readTotal bytes read, expected 2 bytes.");
      }
      val bmp180In = new DataInputStream(new ByteArrayInputStream(bytesTemp));
      bmp180In.readUnsignedShort();
    } catch {
      case ioe: IOException => {
        log.error("IO Exception: " + ioe.getMessage(), ioe)
        -400
      }
      case ie: InterruptedException => {
        log.error("Interrupted Exception: " + ie.getMessage(), ie)
        -400
      }
    }
  }
  
  def readTemp(): Int = {
    val UT = readRawTemp()
    
    //calculate temperature
    val X1 = ((UT - AC6) * AC5) >> 15;
    val X2 = (MC << 11) / (X1 + MD);
    val B5 = X1 + X2;
    val milliC = ((B5 + 8) >> 4) * 100;
    log.debug("Temperature: " + milliC + "mC");
    return milliC;
  }
  

  /**
   * @return Reads the raw (uncompensated) pressure level from the sensor.
   */
  def readRawPressure(): Int = {
    bmp180.write(Bmp180Sensor.controlRegister, (Bmp180Sensor.getPressureCmd + (mode << 6).toByte).toByte)
    mode match {
      case Bmp180Sensor.ULTRALOWPOWER => Thread.sleep(5)
      case Bmp180Sensor.HIGHRES => Thread.sleep(14)
      case Bmp180Sensor.ULTRAHIGHRES => Thread.sleep(26)
      case _ => Thread.sleep(8)
    }
    val msb: Int = bmp180.read(Bmp180Sensor.pressureAddr)
    val lsb: Int = bmp180.read(Bmp180Sensor.pressureAddr + 1)
    val xlsb: Int = bmp180.read(Bmp180Sensor.pressureAddr + 2)
    val raw = ((msb << 16) + (lsb << 8) + xlsb) >> (8 - mode)
    log.debug("Raw pressure 0x{0:04X} ({1})'.format(raw & 0xFFFF, raw)")
    return raw
  }
  
  /**
   * Gets the compensated pressure in Pascals.
   */
  def readPressure(): Int = {
    import calibration._
    val UT = readRawTemp()
    val UP = readRawPressure()
    // Datasheet values for debugging:
    //UT = 27898
    //UP = 23843
    // Calculations below are taken straight from section 3.5 of the datasheet.
    // Calculate true temperature coefficient B5.
    var X1 = ((UT - AC6) * AC5) >> 15
    var X2 = (MC << 11) / (X1 + MD)
    val B5 = X1 + X2
    log.debug(s"B5 = $B5")
    // Pressure Calculations
    val B6 = B5 - 4000
    log.debug(s"B6 = $B6")
    X1 = (B2 * (B6 * B6) >> 12) >> 11
    X2 = (AC2 * B6) >> 11
    var X3 = X1 + X2
    val B3 = (((AC1 * 4 + X3) << mode) + 2) / 4
    log.debug(s"B3 = $B3")
    X1 = (AC3 * B6) >> 13
    X2 = (B1 * ((B6 * B6) >> 12)) >> 16
    X3 = ((X1 + X2) + 2) >> 2
    val B4 = (AC4 * (X3 + 32768)) >> 15
    log.debug(s"B4 = $B4")
    val B7 = (UP - B3) * (50000 >> mode)
    log.debug(s"B7 = $B7")
    var p = if (B7 < 0x80000000) {
      (B7 * 2) / B4
    } else {
      (B7 / B4) * 2
    }
    X1 = (p >> 8) * (p >> 8)
    X1 = (X1 * 3038) >> 16
    X2 = (-7357 * p) >> 16
    p = p + ((X1 + X2 + 3791) >> 4)
    log.debug(s"Pressure $p Pa")
    p
  }

  /**
   * Calculates the altitude in meters.
   */
  def readAltitude(sealevel_pa: Double = 101325.0): Double = {
    // Calculation taken straight from section 3.6 of the datasheet.
    val pressure = readPressure()
    val altitude = 44330.0 * (1.0 - Math.pow(pressure / sealevel_pa, (1.0/5.255)))
    log.debug(s"Altitude $altitude m")
    return altitude
  }

  /**
   *     """Calculates the pressure at sealevel when given a known altitude in
    meters. Returns a value in Pascals."""
   * 
   */
  def readSealevelPressure(altitude_m: Double = 0.0): Double = {
    val pressure = readPressure()
    val p0 = pressure / Math.pow(1.0 - altitude_m/44330.0, 5.255)
    log.debug(s"Sealevel pressure $p0 Pa")
    return p0
  }
}