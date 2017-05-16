package org.reactivecouchbase.webstack.mvc

import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import io.undertow.server.handlers.{CookieImpl, Cookie => UndertowCookie}
import org.apache.commons.codec.digest.DigestUtils
import org.reactivecouchbase.webstack.config.Configuration
import org.reactivecouchbase.webstack.env.{EnvLike, Env}
import org.reactivecouchbase.webstack.libs.Codecs
import play.api.libs.json.{JsObject, JsString, Json}

import scala.util.Try

case class Cookie(
 name: String,
 value: String = "",
 path: String = "/",
 domain: String = "localhost",
 maxAge: Integer = -1,
 discard: Boolean = false,
 secure: Boolean = false,
 httpOnly: Boolean = false,
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

  private[webstack] def sign(message: String, key: Array[Byte]): String = {
    val mac = Mac.getInstance("HmacSHA1")
    mac.init(new SecretKeySpec(key, "HmacSHA1"))
    Codecs.toHexString(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)))
  }

  private[webstack] def fromCookie(cookie: Cookie)(implicit env: EnvLike = Env): Option[Session] = {
    Try {
      val signature = cookie.value.split(":::")(0)
      val value = cookie.value.split(":::")(1)
      val maybeSignature = Session.sign(value, env.sessionConfig.secret)
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
  private[webstack] def asCookie(implicit env: EnvLike = Env): Cookie = {
    val value = Json.stringify(JsObject.apply(underlying.mapValues(JsString.apply)))
    Cookie(
      name = env.sessionConfig.cookieName,
      value = s"${Session.sign(value, env.sessionConfig.secret)}:::$value",
      path = env.sessionConfig.cookiePath,
      domain = env.sessionConfig.cookieDomain,
      maxAge = env.sessionConfig.cookieMaxAge,
      discard = false,
      secure = env.sessionConfig.cookieSecure,
      httpOnly = env.sessionConfig.cookieHttpOnly
    )
  }
}

class SessionConfig(configuration: Configuration) {
  lazy val cookieDomain = configuration.getString("app.session.domain").getOrElse("localhost")
  lazy val cookieName = configuration.getString("app.session.cookieName").getOrElse("webstack-session")
  lazy val cookiePath = configuration.getString("app.session.path").getOrElse("/")
  lazy val cookieSecure = configuration.getBoolean("app.session.secure").getOrElse(true)
  lazy val cookieHttpOnly = configuration.getBoolean("app.session.httpOnly").getOrElse(true)
  lazy val cookieMaxAge = configuration.getInt("app.session.maxAge").getOrElse(-1)
  lazy val secret = DigestUtils
                      .md5Hex(configuration.getString("app.secret").getOrElse("Some hardcoded value here"))
                      .getBytes(StandardCharsets.UTF_8)
}
