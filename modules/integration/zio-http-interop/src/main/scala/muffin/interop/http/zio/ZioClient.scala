package muffin.interop.http.zio

import java.net.ConnectException
import java.net.URI
import java.nio.charset.Charset
import scala.util.chaining.given

import cats.data.NonEmptyList
import cats.effect.Sync
import cats.syntax.all.*

import zio.*
import zio.http.{Body as ZBody, Method as ZMethod, *}
import zio.http.ChannelEvent.Read

import muffin.api.BackoffSettings
import muffin.codec.*
import muffin.error.MuffinError
import muffin.http.*
import muffin.model.websocket.domain.*

class ZioClient[R, To[_], From[_]](codec: CodecSupport[To, From])
  extends HttpClient[RHttp[R with Client with Scope], To, From] {

  import codec.given

  def request[In: To, Out: From](
      url: String,
      method: Method,
      body: Body[In],
      headers: Map[String, String],
      params: Params => Params
  ): RIO[R with Client, Out] =
    for {
      response <- mkRequest(url, method, body, headers, params)

      stringResponse <- response.body.asString(Charset.defaultCharset())
      res            <-
        Decode[Out].apply(stringResponse) match {
          case Left(value)  => ZIO.fail(value)
          case Right(value) => ZIO.succeed(value)
        }
    } yield res

  def requestRawData[In: To](
      url: String,
      method: Method,
      body: Body[In],
      headers: Map[String, String],
      params: Params => Params
  ): RIO[R with Client with Scope, Array[Byte]] = mkRequest(url, method, body, headers, params).flatMap(_.body.asArray)

  private def mkRequest[In: To](
      url: String,
      method: Method,
      body: Body[In],
      headers: Map[String, String],
      params: Params => Params
  ) =
    for {
      requestBody <-
        body match {
          case Body.Empty            => ZIO.attempt(ZBody.empty)
          case Body.Json(value)      => ZIO.attempt(ZBody.fromString(Encode[In].apply(value)))
          case Body.RawJson(value)   => ZIO.attempt(ZBody.fromString(value))
          case Body.Multipart(parts) =>
            for {
              boundary <- Boundary.randomUUID
              form = Form.apply(Chunk.fromIterable(
                parts.map {
                  case MultipartElement.StringElement(name, value) => FormField.textField(name, value)
                  case MultipartElement.FileElement(name, payload) =>
                    FormField.binaryField(
                      name,
                      Chunk.fromArray(payload.content),
                      MediaType.apply("application", "octet-stream", false, true),
                      filename = Some(payload.name)
                    )
                }
              ))
            } yield ZBody.fromMultipartForm(form, boundary)
        }

      response <- Client
        .request(
          url + params(Params.Empty).mkString,
          method match {
            case Method.Get    => ZMethod.GET
            case Method.Post   => ZMethod.POST
            case Method.Delete => ZMethod.DELETE
            case Method.Put    => ZMethod.PUT
            case Method.Patch  => ZMethod.PATCH
          },
          Headers(headers.map(Header.Custom.apply).toList),
          content = requestBody
        )
    } yield response

  def websocketWithListeners(
      uri: URI,
      headers: Map[String, String],
      backoffSettings: BackoffSettings,
      listeners: List[EventListener[RHttp[R with Client with Scope]]]
  ): RIO[R with Client with Scope, Unit] = {
    val retryPolicy =
      Schedule.exponential(
        Duration.fromScala(backoffSettings.initialDelay),
        backoffSettings.multiply
      ) || Schedule.fixed(
        Duration.fromScala(backoffSettings.maxDelayThreshold)
      )
    Handler.webSocket { channel =>
      channel
        .receiveAll {
          case Read(WebSocketFrame.Text(payload)) =>
            Decode[Event[RawJson]].apply(payload) match {
              case Left(decoding) => ZIO.fail(decoding)
              case Right(event)   =>
                ZIO.foreachPar(listeners) {
                  _.onEvent(event)
                    .either
                    .map(
                      _.leftMap(err =>
                        MuffinError.Websockets.ListenerError(err.getMessage, event.eventType, err)
                      )
                    )
                }
                  .flatMap {
                    res =>
                      ZIO.foreach(
                        res.collect { case Left(err) => err }
                          .pipe(NonEmptyList.fromList)
                      )(errs =>
                        ZIO.fail(
                          MuffinError.Websockets.FailedWebsocketProcessing(errs)
                        )
                      )
                  }
            }

          case _ => ZIO.unit
        }
    }
      .connect(
        uri.toString,
        Headers(
          headers.toList.foldLeft(List.empty[Header]) {
            case (acc, (k, v)) => Header.Custom(k, v) :: acc
          }
        )
      )
      .retry(
        retryPolicy && Schedule.recurWhile[Throwable] {
          case err: ConnectException => true
          case _                     => false
        }
      )
      .unit
  }

}

object ZioClient {

  def apply[R, I[_]: Sync, To[_], From[_]](codec: CodecSupport[To, From]): I[ZioClient[R, To, From]] =
    Sync[I].delay(
      new ZioClient[R, To, From](codec)
    )

}
