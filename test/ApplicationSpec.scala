
import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._
import play.api.test._
import play.api.test.Helpers._
import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import temperature.TemperatureMeasurement
import org.joda.time.DateTime
import play.api.libs.json.JsArray
import play.api.libs.json.Json
import play.api.libs.json.JsString
import play.api.libs.json.JsObject
import jchart.ChartData
import controllers.TestConfig
import jchart.NoGrouping
import jchart.HourlyGrouping

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 * For more information, consult the wiki.
 */
@RunWith(classOf[JUnitRunner])
class ApplicationSpec extends Specification {

  "w1reader" should {

    "read test data" in new WithApplication{
      import w1reader._
      import java.io.File

      val reader = new W1Temperatures(new File("test/data"))
      val resultsFuture = reader.filenameAndTempList
      val results = Await.result(resultsFuture, Duration(1, "second"))
      
      results.size must equalTo(2)
      results.head must equalTo(("28-000006debed5", 14562))
    }
  }
  
  val emptyConfig = TestConfig(Map())
  val chartData = new ChartData(emptyConfig)
  
  val testTime = new DateTime("2016-01-02T18:24:21")
  val beforeTestTime = testTime.minusHours(1)
  val afterTestTime = testTime.plusHours(1)
  
  "ChartData generator" should {
    "create chart data json from TemperatureMeasurements" in new WithApplication{
      val m = TemperatureMeasurement(1234, testTime, "outside", 12345)
      val r = chartData.fromMeasurements(beforeTestTime, afterTestTime, Seq("inside1" -> Seq(m)), NoGrouping)
      
      r.fields.toMap.keys.toList must equalTo("labels" :: "datasets" :: Nil)
      (r \ "labels").as[JsArray] must equalTo(Json.arr("2.1 - 17h","17h","17h","18h","18h","18h","18h","18h","18h","19h","19h","19h"))
      val ds1 = (r \ "datasets").as[JsArray].value.head 
      (ds1 \ "label").as[JsString].value must equalTo("outside")
    }
    
    "group results hourly and with no groups" in  new WithApplication{
      val m = TemperatureMeasurement(1234, testTime, "outside", 12345)
      val r = chartData.fromMeasurements(beforeTestTime, afterTestTime, Seq("inside1" -> Seq(m)), HourlyGrouping)
      
      r.fields.toMap.keys.toList must equalTo("labels" :: "datasets" :: Nil)
      (r \ "labels").as[JsArray] must equalTo(Json.arr("2.1 - 18h","19h"))
      val ds1 = (r \ "datasets").as[JsArray].value.head 
      (ds1 \ "label").as[JsString].value must equalTo("outside")
      
      val r2 = chartData.fromMeasurements(beforeTestTime, afterTestTime, Seq("inside1" -> Seq(m)), NoGrouping)
      
      r2.fields.toMap.keys.toList must equalTo("labels" :: "datasets" :: Nil)
      (r2 \ "labels").as[JsArray] must equalTo(Json.arr("2.1 - 17h","17h","17h","18h","18h","18h","18h","18h","18h","19h","19h","19h"))
      val ds12 = (r2 \ "datasets").as[JsArray].value.head 
      (ds12 \ "label").as[JsString].value must equalTo("outside")
    }
    
  }
}
