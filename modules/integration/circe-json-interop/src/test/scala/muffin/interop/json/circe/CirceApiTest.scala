package muffin.interop.json.circe

import java.net.URI

import cats.effect.IO
import cats.syntax.all.*

import io.circe.*
import org.scalatest.*
import org.scalatest.featurespec.AsyncFeatureSpec

import muffin.api.*
import muffin.http.{Body, EventListener, HttpClient, Method, Params}
import muffin.model.websocket.domain.{Event, EventType, RawJson}

class CirceApiTest extends ApiTest[Encoder, Decoder]("circe", codec) {

  protected def toContext: Encoder[AppContext]   = io.circe.Derivation.summonEncoder[AppContext]
  protected def fromContext: Decoder[AppContext] = io.circe.Derivation.summonDecoder[AppContext]

  given Decoder[domain.TestObject] = Decoder.derived
  given Encoder[domain.TestObject] = Encoder.AsObject.derived

  protected def httpClient: HttpClient[IO, Encoder, Decoder] =
    new HttpClient[IO, Encoder, Decoder] {

      def request[In: Encoder, Out: Decoder](
          url: String,
          method: Method,
          body: Body[In],
          headers: Map[String, String],
          params: Params => Params
      ): IO[Out] =
        (body match {
          case Body.Empty            => testRequest(url, method, None, params(Params.Empty))
          case Body.Json(value)      =>
            testRequest(url, method, Encoder[In].apply(value).noSpaces.some, params(Params.Empty))
          case Body.RawJson(value)   =>
            testRequest(url, method, parser.parse(value).map(_.noSpaces).toOption, params(Params.Empty))
          case Body.Multipart(parts) => ???
        }).flatMap(parseJson(_))

      def requestRawData[In: Encoder](
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

    }

  private val events = loadResource(
    "websockets/posting/postingWithFileIds.json"
  )
    .map(postingEvent =>
      List(
        Event(
          EventType.Hello,
          RawJson.from(Encoder[domain.TestObject].apply(domain.TestObject.default).toString)
        ),
        Event(EventType.Posted, RawJson.from(postingEvent))
      )
    )

}
