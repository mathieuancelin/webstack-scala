package org.reactivecouchbase.webstack.config

import com.typesafe.config.{Config, ConfigFactory}

import scala.collection.JavaConversions._
import scala.util.Try

case class Configuration(underlying: Config) {

  private def readValue[T](path: String, supplier: => T): Option[T] = {
    Try(Some(supplier)).getOrElse(None)
  }

  private def readList[T](path: String, supplier: => Seq[T]): Seq[T] = {
    Try(Some(supplier).getOrElse(Seq.empty[T])).getOrElse(Seq.empty[T])
  }

  def getString(path: String): Option[String] = {
    readValue(path, underlying.getString(path))
  }

  def getInt(path: String): Option[Int] = {
    readValue(path, underlying.getInt(path))
  }

  def getBoolean(path: String): Option[Boolean] = {
    readValue(path, underlying.getBoolean(path))
  }

  def getDouble(path: String): Option[Double] = {
    readValue(path, underlying.getDouble(path))
  }

  def getLong(path: String): Option[Long] = {
    readValue(path, underlying.getLong(path))
  }

  def getDoubleList(path: String): Seq[Double] = {
    readList(path, underlying.getDoubleList(path).map(_.toDouble))
  }

  def getIntList(path: String): Seq[Int] = {
    readList(path, underlying.getIntList(path).map(_.toInt))
  }

  def getLongList(path: String): Seq[Long] = {
    readList(path, underlying.getLongList(path).map(_.toLong))
  }

  def getStringList(path: String): Seq[String] = {
    readList(path, underlying.getStringList(path))
  }

  def at(path: String): Configuration = {
    new Configuration(underlying.atPath(path).withFallback(ConfigFactory.empty()))
  }
}