package org.reactivecouchbase.webstack

import java.nio.charset.StandardCharsets

import akka.stream.scaladsl.Source
import akka.util.ByteString

import scala.collection.immutable.{Iterable => IMterable}

object StreamUtils {

  val defaultChunkSize = 8192

  def bytesToSource(value: Array[Byte], groupBy: Option[Int] = None): Source[ByteString, _] = {
    groupBy match {
      case Some(chunk) => Source[ByteString](IMterable.concat(value.grouped(chunk).map(ByteString.apply).toSeq)) // PERFS ?
      case None => Source.single(ByteString.fromArray(value))
    }
  }

  def stringToSource(value: String, groupBy: Option[Int] = None): Source[ByteString, _] = {
    groupBy match {
      case Some(chunk) => bytesToSource(value.getBytes(StandardCharsets.UTF_8), Some(chunk)) // PERFS ?
      case None => Source.single(ByteString.fromString(value))
    }
  }
}
