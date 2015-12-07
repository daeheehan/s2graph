package com.kakao.s2graph.rest.finagle

import java.util.concurrent.Executors

import com.kakao.s2graph.core._
import com.kakao.s2graph.core.utils.logger
import com.twitter.finagle
import com.typesafe.config.ConfigFactory
import play.api.libs.json.Json

import scala.concurrent.ExecutionContext
import scala.util.Success

object FinagleServer extends App {

  import com.twitter.finagle.{Service, _}
  import com.twitter.util._

  val numOfThread = Runtime.getRuntime.availableProcessors()
  val threadPool = Executors.newFixedThreadPool(numOfThread)
  implicit val ec = ExecutionContext.fromExecutor(threadPool)

  val config = ConfigFactory.load()

  // init s2graph with config
  val s2graph = new Graph(config)(ec)
  val s2parser = new RequestParser(s2graph)
  var isHealthy = true

  val service = new Service[http.Request, http.Response] {

    def apply(req: http.Request): Future[http.Response] = {
      val promise = new com.twitter.util.Promise[http.Response]
      val payload = req.contentString

      //      def route(req: http.Request)(pf: (http.Request, Promise[_]) => Unit): Future[http.Response] = {
      //        val promise = new com.twitter.util.Promise[http.Response]
      //        pf.apply(req, promise)
      //        promise
      //      }
      val startedAt = System.currentTimeMillis()

      req.method match {
        case finagle.http.Method.Post =>
          req.path match {
            case path if path.startsWith("/graphs/getEdges") =>
              val bodyAsJson = Json.parse(payload)
              val query = s2parser.toQuery(bodyAsJson)
              val fetch = s2graph.getEdges(query)
              fetch.onComplete {
                case Success(queryRequestWithResutLs) =>
                  val jsValue = PostProcess.toSimpleVertexArrJson(queryRequestWithResutLs, Nil)

                  val httpRes = {
                    val response = finagle.http.Response(finagle.http.Version.Http11, finagle.http.Status.Ok)
                    response.setContentTypeJson()
                    response.setContentString(jsValue.toString)
                    response
                  }

                  val duration = System.currentTimeMillis() - startedAt
                  val str = s"${req.method} ${req.uri} took ${duration} ms ${200} ${-1} ${payload}"
                  logger.info(str)

                  promise.become(Future.value(httpRes))
              }
          }

        case finagle.http.Method.Get =>
          req.path match {
            case path if path.startsWith("/health_check") =>
              val httpRes = {
                val response = finagle.http.Response(finagle.http.Version.Http11, finagle.http.Status.Ok)
                response.setContentType("text/plain")
                response.setContentString(isHealthy.toString)
                response
              }
              promise.become(Future.value(httpRes))
          }

         case finagle.http.Method.Put =>
          req.path match {
            case path if path.startsWith("/health_check") =>
              val op = path.split("/").last.toBoolean
              isHealthy = op

              val httpRes = {
                val response = finagle.http.Response(finagle.http.Version.Http11, finagle.http.Status.Ok)
                response.setContentType("text/plain")
                response.setContentString(isHealthy.toString)
                response
              }
              promise.become(Future.value(httpRes))
          }
      }

      promise
    }
  }

  val port = try config.getInt("http.port") catch { case e: Exception => 9000 }
  val server = Http.serve(s":$port", FinagleServer.service)

  Await.ready(server)
}

