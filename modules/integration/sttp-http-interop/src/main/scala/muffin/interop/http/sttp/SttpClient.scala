package muffin.interop.http.sttp

import java.net.URI
import scala.util.chaining.given

import cats.{MonadThrow, Parallel}
import cats.data.NonEmptyList
import cats.effect.{Sync, Temporal}
import cats.syntax.all.given
import fs2.*

import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3.*
import sttp.model.{MediaType, Method as SMethod, Uri}
import sttp.ws.WebSocketFrame

import muffin.api.BackoffSettings
import muffin.codec.*
import muffin.error.MuffinError
import muffin.http.*
import muffin.internal.syntax.*
import muffin.model.websocket.domain.*

class SttpClient[F[_]: Temporal: Parallel, To[_], From[_]](
    backend: SttpBackend[F, Fs2Streams[F] & WebSockets],
    codec: CodecSupport[To, From]
) extends HttpClient[F, To, From] {

  import codec.given

  def request[In: To, Out: From](
      url: String,
      method: Method,
      body: Body[In],
      headers: Map[String, String],
      params: Params => Params
  ): F[Out] = {
    val req = mkRequest(url, method, body, headers, params)
      .response(asString.mapLeft(MuffinError.Http.apply))
      .mapResponse(_.flatMap(Decode[Out].apply))

    backend.send(req)
      .map(_.body)
      .flatMap {
        case Left(error)  => MonadThrow[F].raiseError(error)
        case Right(value) => value.pure[F]
      }
  }

  def requestRawData[In: To](
      url: String,
      method: Method,
      body: Body[In],
      headers: Map[String, String],
      params: Params => Params
  ): F[Array[Byte]] = {
    val req = mkRequest(url, method, body, headers, params)
      .response(asByteArray.mapLeft(MuffinError.Http.apply))

    backend.send(req)
      .map(_.body)
      .flatMap {
        case Left(error)  => MonadThrow[F].raiseError(error)
        case Right(value) => value.pure[F]
      }
  }

  private def mkRequest[In: To, Out: From, R >: Fs2Streams[F] & WebSockets](
      url: String,
      method: Method,
      body: Body[In],
      headers: Map[String, String],
      params: Params => Params
  ) =
    basicRequest
      .method(
        method match {
          case Method.Get    => SMethod.GET
          case Method.Post   => SMethod.POST
          case Method.Put    => SMethod.PUT
          case Method.Delete => SMethod.DELETE
          case Method.Patch  => SMethod.PATCH
        },
        Uri.unsafeParse(url + params(Params.Empty).mkString)
      )
      .headers(headers)
      .tap {
        req =>
          body match {
            case Body.Empty            => req
            case Body.Json(value)      =>
              req
                .body(Encode[In].apply(value), "UTF-8")
                .header("Content-Type", "application/json")
            case Body.RawJson(value)   =>
              req
                .body(value, "UTF-8")
                .header("Content-Type", "application/json")
            case Body.Multipart(parts) =>
              req
                .multipartBody(
                  parts.map {
                    case MultipartElement.StringElement(name, value) => multipart(name, value)

                    case MultipartElement.FileElement(name, payload) =>
                      multipart(name, payload.content).fileName(payload.name)
                  }
                )
                .contentType(MediaType.MultipartFormData)
          }
      }

  def websocketWithListeners(
      uri: URI,
      headers: Map[String, String] = Map.empty,
      backoffSettings: BackoffSettings,
      listeners: List[EventListener[F]] = Nil
  ): F[Unit] = {
    val websocketEventProcessing: Pipe[F, WebSocketFrame.Data[?], WebSocketFrame] = { input =>
      input.flatMap {
        case WebSocketFrame.Text(payload, _, _) =>
          Stream.eval(
            Decode[Event[RawJson]].apply(payload).liftTo[F] >>= {
              event =>
                listeners
                  .parTraverse(
                    _.onEvent(event)
                      .attempt
                      .map(
                        _.leftMap(err =>
                          MuffinError.Websockets.ListenerError(err.getMessage, event.eventType, err)
                        )
                      )
                  ) >>= {
                  _.collect { case Left(err) => err }
                    .pipe(NonEmptyList.fromList)
                    .traverse_(
                      MuffinError.Websockets.FailedWebsocketProcessing(_).raiseError[F, Unit]
                    )
                }
            }
          ) *>
          Stream.empty

        case _ => Stream.empty
      }
    }

    val request = basicRequest
      .headers(headers)
      .response(
        asWebSocketStream(Fs2Streams[F])(
          websocketEventProcessing
        )
          .mapLeft(MuffinError.Websockets.Websocket(_))
      )
      .get(uri"${uri.toString}")
      .send(backend)
      .map(_.body)
      .flatMap {
        case Left(error)  => MonadThrow[F].raiseError(error)
        case Right(value) => value.pure[F]
      }

    retryWithBackoff(request, backoffSettings)

  }

  private def retryWithBackoff[A](f: F[A], backoffSettings: BackoffSettings): F[A] =
    f
      .handleErrorWith {
        case _: SttpClientException.ConnectException |
            _: SttpClientException.TimeoutException |
            _: SttpClientException.ReadException =>
          Temporal[F].sleep(
            backoffSettings.initialDelay min backoffSettings.maxDelayThreshold
          ) *> retryWithBackoff(
            f,
            backoffSettings
              .copy(initialDelay =
                (backoffSettings.initialDelay * backoffSettings.multiply) min backoffSettings.maxDelayThreshold
              )
          )
      }
      .flatMap(_ => retryWithBackoff(f, backoffSettings))

}

object SttpClient {

  def apply[I[_]: Sync, F[_]: Temporal: Parallel, To[_], From[_]](
      backend: SttpBackend[F, Fs2Streams[F] & WebSockets],
      codec: CodecSupport[To, From]
  ): I[SttpClient[F, To, From]] = Sync[I].delay(new SttpClient[F, To, From](backend, codec))

}
