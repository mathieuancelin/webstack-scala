package org.reactivecouchbase.webstack.result

import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import io.undertow.server.handlers.{CookieImpl, Cookie => UndertowCookie}
import org.apache.commons.codec.digest.DigestUtils
import org.reactivecouchbase.webstack.env.Env
import org.reactivecouchbase.webstack.libs.Codecs
import play.api.libs.json.{JsObject, JsString, Json}

import scala.util.Try

case class Cookie(
 name: String,
 value: String,
 path: String,
 domain: String,
 maxAge: Integer,
 discard: Boolean = false,
 secure: Boolean,
 httpOnly: Boolean,
 version: Int = 0,
 comment: String = ""
) {
  private[webstack] def undertowCookie: UndertowCookie = {
    new CookieImpl(name)
      .setValue(value)
      .setPath(path)
      .setDomain(domain)
      .setMaxAge(maxAge)
      .setDiscard(discard)
      .setSecure(secure)
      .setHttpOnly(httpOnly)
      .setComment(comment)
      .setVersion(version)
  }
}

object Cookie {

  private[webstack] def apply(cookie: UndertowCookie): Cookie = Cookie(
    name = cookie.getName,
    value = cookie.getValue,
    path = cookie.getPath,
    domain = cookie.getDomain,
    maxAge = cookie.getMaxAge,
    discard = cookie.isDiscard,
    secure = cookie.isSecure,
    httpOnly = cookie.isHttpOnly,
    version = cookie.getVersion,
    comment = cookie.getComment
  )
}

object Session {
  private[webstack] lazy val cookieDomain = Env.configuration.getString("app.session.domain").getOrElse("localhost")
  private[webstack] lazy val cookieName = Env.configuration.getString("app.session.cookieName").getOrElse("webstack-session")
  private[webstack] lazy val cookiePath = Env.configuration.getString("app.session.path").getOrElse("/")
  private[webstack] lazy val cookieSecure = Env.configuration.getBoolean("app.session.secure").getOrElse(true)
  private[webstack] lazy val cookieHttpOnly = Env.configuration.getBoolean("app.session.httpOnly").getOrElse(true)
  private[webstack] lazy val cookieMaxAge = Env.configuration.getInt("app.session.maxAge").getOrElse(-1)
  private[webstack] lazy val secret = DigestUtils.md5Hex(Env.configuration.getString("app.secret").getOrElse("Some hardcoded value here"))
    .getBytes(StandardCharsets.UTF_8)
  private[webstack] def sign(message: String, key: Array[Byte]): String = {
    val mac = Mac.getInstance("HmacSHA1")
    mac.init(new SecretKeySpec(key, "HmacSHA1"))
    Codecs.toHexString(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)))
  }
  private[webstack] def fromCookie(cookie: Cookie): Option[Session] = {
    Try {
      val signature = cookie.value.split(":::")(0)
      val value = cookie.value.split(":::")(1)
      val maybeSignature = Session.sign(value, Session.secret)
      if (signature == maybeSignature) {
        val map: collection.Map[String, String] = Json.parse(value).as[JsObject].value.mapValues(_.as[String])
        Some(Session(map))
      } else {
        None
      }
    }.toOption.flatten
  }
}

case class Session(underlying: collection.Map[String, String] = Map.empty[String, String]) {
  def add(tuple: (String, String)*): Session = copy(underlying = underlying ++ tuple)
  def +(tuple: (String, String)*): Session = copy(underlying = underlying ++ tuple)
  def remove(key: String*): Session = copy(underlying = underlying -- key)
  def -(key: String*): Session = copy(underlying = underlying -- key)
  def get(key: String): Option[String] = underlying.get(key)
  def apply(key: String): Option[String] = underlying.get(key)
  private[webstack] def asCookie: Cookie = {
    val value = Json.stringify(JsObject.apply(underlying.mapValues(JsString.apply)))
    Cookie(
      name = Session.cookieName,
      value = Session.sign(value, Session.secret) + ":::" + value,
      path = Session.cookiePath,
      domain = Session.cookieDomain,
      maxAge = Session.cookieMaxAge,
      discard = false,
      secure = Session.cookieSecure,
      httpOnly = Session.cookieHttpOnly
    )
  }
}