package org.reactivecouchbase.webstack

import java.nio.charset.StandardCharsets

import akka.stream.scaladsl.Source
import akka.util.ByteString

import scala.collection.immutable.{Iterable => IMterable}

object StreamUtils {
  def bytesToSource(value: Array[Byte], chunk: Int = 8192): Source[ByteString, _] = {
    // PERFS ?
    Source[ByteString](IMterable.concat(value.grouped(chunk).map(ByteString.apply).toSeq))
  }

  def stringToSource(value: String, chunk: Int = 8192): Source[ByteString, _] = {
    // PERFS ?
    bytesToSource(value.getBytes(StandardCharsets.UTF_8), chunk)
  }
}
