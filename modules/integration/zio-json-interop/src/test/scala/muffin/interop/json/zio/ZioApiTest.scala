package muffin.interop.json.zio

import java.net.URI

import cats.effect.IO
import cats.syntax.all.*

import org.scalatest.*
import org.scalatest.featurespec.AsyncFeatureSpec
import zio.*
import zio.json.*

import muffin.api.{domain, ApiTest, BackoffSettings}
import muffin.http.{Body, EventListener, HttpClient, Method, Params}
import muffin.model.websocket.domain.*

class ZioApiTest extends ApiTest[JsonEncoder, JsonDecoder]("zio", codec) {

  protected def toContext: JsonEncoder[AppContext]   = JsonEncoder.derived[AppContext]
  protected def fromContext: JsonDecoder[AppContext] = JsonDecoder.derived[AppContext]

  protected def httpClient: HttpClient[IO, JsonEncoder, JsonDecoder] = {
    given JsonEncoder[domain.TestObject] = JsonEncoder.derived

    new HttpClient[IO, JsonEncoder, JsonDecoder] {

      def request[In: JsonEncoder, Out: JsonDecoder](
          url: String,
          method: Method,
          body: Body[In],
          headers: Map[String, String],
          params: Params => Params
      ): IO[Out] =
        (body match {
          case Body.Empty            => testRequest(url, method, None, params(Params.Empty))
          case Body.Json(value)      =>
            testRequest(url, method, JsonEncoder[In].encodeJson(value, None).toString.some, params(Params.Empty))
          case Body.RawJson(value)   => testRequest(url, method, value.some, params(Params.Empty))
          case Body.Multipart(parts) => ??? // TODO implement file tests
        }).flatMap(str =>
          str.fromJson[Out] match {
            case Left(message) => IO.raiseError(new Exception(message))
            case Right(value)  => value.pure[IO]
          }
        )

      def requestRawData[In: JsonEncoder](
          url: String,
          method: Method,
          body: Body[In],
          headers: Map[String, String],
          params: Params => Params
      ): IO[Array[Byte]] = IO("wubba lubba dub dub".getBytes)

      def websocketWithListeners(
          uri: URI,
          headers: Map[String, String],
          backoffSettings: BackoffSettings,
          listeners: List[EventListener[IO]]
      ): IO[Unit] = events.flatMap(_.traverse_(event => listeners.traverse_(_.onEvent(event))))

      private val events = loadResource("websockets/posting/postingWithFileIds.json").map(postingEvent =>
        List(
          Event(
            EventType.Hello,
            RawJson.from(domain.TestObject.default.toJson)
          ),
          Event(EventType.Posted, RawJson.from(postingEvent))
        )
      )
    }
  }

}
