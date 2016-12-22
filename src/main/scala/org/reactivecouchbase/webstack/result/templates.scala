package org.reactivecouchbase.webstack.result

import java.util.concurrent.ConcurrentHashMap

import com.github.jknack.handlebars.io.ClassPathTemplateLoader
import com.github.jknack.handlebars.{Handlebars, Template}

import scala.util.Try

case class TemplateConfig(path: String, extension: String)

class TemplatesResolver(prefix: String, suffix: String) {

  private val loader = {
    val cptl = new ClassPathTemplateLoader
    cptl.setPrefix(prefix)
    cptl.setSuffix(suffix)
    cptl
  }

  private val handlebars = new Handlebars(loader)

  private val TEMPLATES_CACHE = new ConcurrentHashMap[String, Template]

  // TODO : option ?
  def getTemplate(name: String): Template = {
    if (!TEMPLATES_CACHE.containsKey(name)) {
      Try {
        val template: Template = handlebars.compile(name)
        TEMPLATES_CACHE.putIfAbsent(name, template)
      } get
    }
    TEMPLATES_CACHE.get(name)
  }
}
