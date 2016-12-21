package org.reactivecouchbase.webstack.libs

import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.codec.binary.Hex

object Codecs {
  def sha1(bytes: Array[Byte]): String = DigestUtils.sha1Hex(bytes)
  def md5(bytes: Array[Byte]): String = DigestUtils.md5Hex(bytes)
  def sha1(text: String): String = DigestUtils.sha1Hex(text)
  def toHex(array: Array[Byte]): Array[Char] = Hex.encodeHex(array)
  def toHexString(array: Array[Byte]): String = Hex.encodeHexString(array)
  def hexStringToByte(hexString: String): Array[Byte] = Hex.decodeHex(hexString.toCharArray)
}