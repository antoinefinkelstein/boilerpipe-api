package com.blikk.boilerpipeapi

import java.io.StringReader

import de.l3s.boilerpipe.extractors.ArticleExtractor
import de.l3s.boilerpipe.sax.{BoilerpipeSAXInput, ImageExtractor, HTMLHighlighter}
import spray.json._

import scala.collection.JavaConverters._
import akka.actor._
import akka.http.scaladsl.model.HttpResponse
import akka.stream.ActorMaterializer
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import org.xml.sax.InputSource
import spray.json._
import spray.json.DefaultJsonProtocol._

import scala.None

object Server extends App {
  implicit val system = ActorSystem()
  import system.dispatcher
  implicit val materializer = ActorMaterializer()

  case class ExtractionRequest(
    html: Option[String],
    url: Option[String],
    extract_html: Option[Boolean],
    extract_images: Option[Boolean]
  )
  implicit val _ = jsonFormat4(ExtractionRequest)

  val routes =
    path("extract") {
      post { decodeRequest { entity(as[ExtractionRequest]) { req =>
        val articleExtractor = ArticleExtractor.getInstance()
        val imageExtractor = ImageExtractor.getInstance()

        val hh = HTMLHighlighter.newExtractingInstance()
        req match {
          case ExtractionRequest(Some(html), _, Some(extract_html), _) => complete {
            val doc = new BoilerpipeSAXInput(new InputSource(new StringReader(html))).getTextDocument()
            articleExtractor.process(doc)
            val is = new InputSource(new StringReader(html))
            hh.process(doc, is)
          }

          case ExtractionRequest(None, Some(url), Some(extract_html), _) => complete {
            hh.process(new java.net.URL(url), articleExtractor)
          }

          case ExtractionRequest(Some(html), _, _, Some(extract_images)) => complete {
            val doc = new BoilerpipeSAXInput(new InputSource(new StringReader(html))).getTextDocument()
            articleExtractor.process(doc)

            val is = new InputSource(new StringReader(html))

            val images = imageExtractor.process(doc, is).asScala.toList.map({
              img => img.getSrc
            })
            images.toJson
          }

          case ExtractionRequest(Some(html), _, _, _) => complete {
            articleExtractor.getText(html)
          }

          case ExtractionRequest(None, Some(url), _, _) => complete {
            articleExtractor.getText(new java.net.URL(url))
          }
        }
      }}}
    }

  val port = sys.env.getOrElse("PORT", "3000").toInt
  val bindingFuture = Http().bindAndHandle(routes, "0.0.0.0", port)
  system.awaitTermination()
}
