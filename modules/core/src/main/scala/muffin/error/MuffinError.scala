package muffin.error

import cats.Show
import cats.data.NonEmptyList
import cats.syntax.option.given

import muffin.model.websocket.domain.EventType

sealed abstract class MuffinError(message: String, cause: Option[Throwable] = None)
  extends Throwable(message, cause.orNull)

object MuffinError {

  case class Decoding(message: String) extends MuffinError(message)

  case class Http(message: String) extends MuffinError(message)

  object Websockets {
    case class Websocket(message: String) extends MuffinError(message)

    case class ListenerError(message: String, eventType: EventType, cause: Throwable)
      extends MuffinError(message, cause.some)

    object ListenerError {
      given Show[ListenerError] = Show.show[ListenerError](_.toString) // todo: where to place
    }

    case class FailedWebsocketProcessing(errors: NonEmptyList[ListenerError])
      extends MuffinError(errors.show[ListenerError])

  }

}
