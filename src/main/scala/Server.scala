package com.blikk.boilerpipeapi

import java.io.StringReader

import akka.actor._
import akka.stream.ActorMaterializer
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import de.l3s.boilerpipe.document.TextDocument
import de.l3s.boilerpipe.sax.{BoilerpipeSAXInput, HTMLHighlighter}
import org.xml.sax.InputSource
import spray.json._
import spray.json.DefaultJsonProtocol._
import de.l3s.boilerpipe.extractors.ArticleExtractor

import scala.None

object Server extends App {
  implicit val system = ActorSystem()
  import system.dispatcher
  implicit val materializer = ActorMaterializer()

  case class ExtractionRequest(
    html: Option[String],
    url: Option[String],
    extract_html: Option[Boolean]
  )
  implicit val _ = jsonFormat3(ExtractionRequest)

  val routes =
    path("extract") {
      post { decodeRequest { entity(as[ExtractionRequest]) { req =>
        val extractor = ArticleExtractor.getInstance()
        val hh = HTMLHighlighter.newExtractingInstance()
        req match {
          case ExtractionRequest(Some(html), _, Some(extract_html)) => complete {
            val doc = new BoilerpipeSAXInput(new InputSource(new StringReader(html))).getTextDocument()
            extractor.process(doc)
            val is = new InputSource(new StringReader(html))
            hh.process(doc, is)
          }
          case ExtractionRequest(None, Some(url), Some(extract_html)) => complete { hh.process(new java.net.URL(url), extractor) }
          case ExtractionRequest(Some(html), _, _) => complete { extractor.getText(html) }
          case ExtractionRequest(None, Some(url), _) => complete { extractor.getText(new java.net.URL(url)) }
        }
      }}}
    }

  val port = Option(sys.env("PORT")).getOrElse("3000").toInt
  val bindingFuture = Http().bindAndHandle(routes, "0.0.0.0", port)
  system.awaitTermination()
}
