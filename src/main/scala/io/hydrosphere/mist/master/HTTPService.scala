package io.hydrosphere.mist.master

import akka.actor.ActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.ActorMaterializer
import akka.pattern.{ask, AskTimeoutException}
import io.hydrosphere.mist.{Constants, MistConfig}

import spray.json.{DefaultJsonProtocol, JsonFormat, JsValue, JsNumber, JsString, JsTrue, JsFalse, serializationError, JsArray, JsObject, deserializationError}
import org.json4s.DefaultFormats
import org.json4s.native.Json

import io.hydrosphere.mist.jobs.{JobResult, JobConfiguration}

import scala.concurrent.ExecutionContext.Implicits.global

import scala.language.reflectiveCalls

private[mist] trait JsonFormatSupport extends DefaultJsonProtocol{
  /** We must implement json parse/serializer for [[Any]] type */
  implicit object AnyJsonFormat extends JsonFormat[Any] {
    def write(x: Any): JsValue = x match {
      case number: Int => JsNumber(number)
      case string: String => JsString(string)
      case sequence: Seq[_] => seqFormat[Any].write(sequence)
      case map: Map[String, _] => mapFormat[String, Any] write map
      case boolean: Boolean if boolean => JsTrue
      case boolean: Boolean if !boolean => JsFalse
      case unknown: Any => serializationError("Do not understand object of type " + unknown.getClass.getName)
    }
    def read(value: JsValue): Any = value match {
      case JsNumber(number) => number.toBigInt()
      case JsString(string) => string
      case array: JsArray => listFormat[Any].read(value)
      case jsObject: JsObject => mapFormat[String, Any].read(value)
      case JsTrue => true
      case JsFalse => false
      case unknown: Any => deserializationError("Do not understand how to deserialize " + unknown)
    }
  }

  // JSON to JobConfiguration mapper (6 fields)
  implicit val jobCreatingRequestFormat = jsonFormat6(JobConfiguration)
}
/** HTTP interface */
private[mist] trait HTTPService extends Directives with SprayJsonSupport with JsonFormatSupport {

  implicit val system: ActorSystem
  implicit val materializer: ActorMaterializer

  // /jobs
  def route : Route = path("jobs") {
    // POST /jobs
    post {
      // POST body must be JSON mapable into JobConfiguration
      entity(as[JobConfiguration]) { jobCreatingRequest =>
        complete {

          println(jobCreatingRequest.parameters)

          // Run job asynchronously
          val workerManagerActor = system.actorSelection(s"akka://mist/user/${Constants.Actors.workerManagerName}")

          val future = workerManagerActor.ask(jobCreatingRequest)(timeout = MistConfig.Contexts.timeout(jobCreatingRequest.name))

          future
            .recover {
              case error: AskTimeoutException => Right(Constants.Errors.jobTimeOutError)
              case error: Throwable => Right(error.toString)
            }
            .map[ToResponseMarshallable] {
              case result: Either[Map[String, Any], String] =>
                val jobResult: JobResult = result match {
                  case Left(jobResults: Map[String, Any]) =>
                    JobResult(success = true, payload = jobResults, request = jobCreatingRequest, errors = List.empty)
                  case Right(error: String) =>
                    JobResult(success = false, payload = Map.empty[String, Any], request = jobCreatingRequest, errors = List(error))
                }
                Json(DefaultFormats).write(jobResult)
            }
        }
      }
    }
  }
}