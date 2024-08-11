package muffin.api

import java.net.URI
import java.net.URISyntaxException

import cats.MonadThrow
import cats.syntax.all.given

import muffin.codec.*
import muffin.http.*
import muffin.model.*
import muffin.model.websocket.domain.*

trait WebsocketBuilder[F[_], To[_], From[_]] {

  def addListener[EventData: From](
      eventType: EventType,
      onEvent: EventData => F[Unit]
  ): Websocket.ConnectionBuilder[F, To, From]

  def connect(): F[Unit]
}

object Websocket {

  class ConnectionBuilder[F[_]: MonadThrow, To[_], From[_]] private (
      httpClient: HttpClient[F, To, From],
      headers: Map[String, String],
      codecSupport: CodecSupport[To, From],
      uri: URI,
      backoffSettings: BackoffSettings,
      listeners: List[EventListener[F]] = Nil
  ) extends WebsocketBuilder[F, To, From] {
    import codecSupport.given

    def connect(): F[Unit] = httpClient.websocketWithListeners(uri, headers, backoffSettings, listeners)

    /** Can repeat listener in case server sends data to client. MM sends event of bot actions
      */
    def addListener[EventData: From](
        eventType: EventType,
        onEventListener: EventData => F[Unit]
    ): ConnectionBuilder[F, To, From] =
      new ConnectionBuilder[F, To, From](
        httpClient,
        headers,
        codecSupport,
        uri,
        backoffSettings,
        new EventListener[F] {

          def onEvent(event: Event[RawJson]): F[Unit] =
            if (eventType != event.eventType) { MonadThrow[F].unit }
            else {
              Decode[EventData].apply(event.data.value).liftTo[F] >>= onEventListener
            }

        } :: listeners
      )

  }

  object ConnectionBuilder {

    def build[F[_]: MonadThrow, To[_], From[_]](
        httpClient: HttpClient[F, To, From],
        headers: Map[String, String],
        codecSupport: CodecSupport[To, From],
        baseUrl: String,
        backoffSettings: BackoffSettings
    ): F[WebsocketBuilder[F, To, From]] =
      prepareWebsocketUri(baseUrl)
        .map(
          new ConnectionBuilder[F, To, From](
            httpClient,
            headers,
            codecSupport,
            _,
            backoffSettings
          )
        )
        .widen[WebsocketBuilder[F, To, From]]

    private def prepareWebsocketUri[F[_]: MonadThrow](raw: String): F[URI] = {
      val init = URI(raw)
      (init.getScheme match {
        case "http"         => (host: String) => URI(s"ws://$host/api/v4/websocket").pure[F]
        case "https"        => (host: String) => URI(s"wss://$host/api/v4/websocket").pure[F]
        case unkownProtocol =>
          (_: String) => (new URISyntaxException(raw, s"unknown schema: $unkownProtocol")).raiseError[F, URI]
      })(init.getAuthority)
    }

  }

}
