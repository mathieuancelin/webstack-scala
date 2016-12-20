package org.reactivecouchbase.webstack.result

import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.io.ClassPathTemplateLoader

case class TemplatesBoilerplate(prefix: String, suffix: String) {
  val loader = new ClassPathTemplateLoader
  loader.setPrefix(prefix)
  loader.setSuffix(suffix)
  val handlebars = new Handlebars(loader)
}
