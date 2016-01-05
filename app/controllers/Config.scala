package controllers

import play.api.Configuration
import com.google.inject.Inject
import com.google.inject.ImplementedBy
import com.typesafe.config.ConfigException.Missing

@ImplementedBy(classOf[RealConfig])
trait Config {
  def requiredString(key: String): String = {
    string(key).getOrElse(throw new Missing(key))
  }
  
  def string(key: String): Option[String]
}

class RealConfig @Inject()(val configuration: Configuration) extends Config {
  override def string(key: String): Option[String] = {
    configuration.getString(key)
  }
}

case class TestConfig(values: Map[String, Any]) extends Config {
  override def string(key: String): Option[String] = {
    values.get(key).map(_.asInstanceOf[String])
  }
}