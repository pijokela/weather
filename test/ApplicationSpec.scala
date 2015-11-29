import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._
import play.api.test._
import play.api.test.Helpers._
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import dao.TemperatureMeasurement
import org.joda.time.DateTime
import jchart.ChartData
import play.api.libs.json.JsArray
import play.api.libs.json.Json
import play.api.libs.json.JsString

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 * For more information, consult the wiki.
 */
@RunWith(classOf[JUnitRunner])
class ApplicationSpec extends Specification {

  "Application" should {

    "send 404 on a bad request" in new WithApplication{
      route(FakeRequest(GET, "/boum")) must beSome.which (status(_) == NOT_FOUND)
    }

//    "render the index page" in new WithApplication{
//      val home = route(FakeRequest(GET, "/")).get
//
//      status(home) must equalTo(OK)
//      contentType(home) must beSome.which(_ == "text/html")
//      contentAsString(home) must contain ("Your new application is ready.")
//    }
  }

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
  
  "ChartData generator" should {
    "create chart data json from TemperatureMeasurements" in new WithApplication{
      val testTime = new DateTime
      val m = TemperatureMeasurement(1234, testTime, "outside", 12345)
      val r = ChartData.fromMeasurements(List(m))
      
      r.fields.toMap.keys.toList must equalTo("labels" :: "datasets" :: Nil)
      (r \ "labels").as[JsArray] must equalTo(Json.arr(testTime.toString("yyyy-MM-dd'T'HH-mm-ss")))
      val ds1 = (r \ "datasets").as[JsArray].value.head 
      (ds1 \ "label").as[JsString].value must equalTo("outside")
    }
    
    "group results hourly and with no groups" in  new WithApplication{
      val testTime = new DateTime
      val m = TemperatureMeasurement(1234, testTime, "outside", 12345)
      val r = ChartData.fromMeasurements(List(m), Some("hourly"))
      
      r.fields.toMap.keys.toList must equalTo("labels" :: "datasets" :: Nil)
      (r \ "labels").as[JsArray] must equalTo(Json.arr(testTime.toString("yyyy-MM-dd'T'HH-mm-ss")))
      val ds1 = (r \ "datasets").as[JsArray].value.head 
      (ds1 \ "label").as[JsString].value must equalTo("outside")
      
      val r2 = ChartData.fromMeasurements(List(m), Some("none"))
      
      r2.fields.toMap.keys.toList must equalTo("labels" :: "datasets" :: Nil)
      (r2 \ "labels").as[JsArray] must equalTo(Json.arr(testTime.toString("yyyy-MM-dd'T'HH-mm-ss")))
      val ds12 = (r2 \ "datasets").as[JsArray].value.head 
      (ds12 \ "label").as[JsString].value must equalTo("outside")
    }
    
  }
}
