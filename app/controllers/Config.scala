package controllers

import play.api.Configuration
import com.google.inject.Inject
import com.google.inject.ImplementedBy
import com.typesafe.config.ConfigException.Missing

@ImplementedBy(classOf[RealConfig])
trait Config {
  def requiredString(key: String): String =
    string(key).getOrElse(throw new Missing(key))
  def requiredBoolean(key: String): Boolean =
    boolean(key).getOrElse(throw new Missing(key))
  
  def string(key: String): Option[String]
  def stringList(key: String): List[String]
  def boolean(key: String): Option[Boolean]
}

class RealConfig @Inject()(val configuration: Configuration) extends Config {
  override def string(key: String): Option[String] =
    configuration.getString(key)
  
  import scala.collection.JavaConversions._
  override def stringList(key: String): List[String] =
    configuration.getStringList(key).toList.flatten
    
  override def boolean(key: String): Option[Boolean] =
    configuration.getBoolean(key)
}

case class TestConfig(values: Map[String, Any]) extends Config {
  override def string(key: String): Option[String] =
    values.get(key).map(_.asInstanceOf[String])

  override def stringList(key: String): List[String] =
    values.get(key).map(_.asInstanceOf[List[String]]).toList.flatten
    
  override def boolean(key: String): Option[Boolean] =
    values.get(key).map(_.asInstanceOf[Boolean])
}