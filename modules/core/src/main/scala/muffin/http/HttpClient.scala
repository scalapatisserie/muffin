package muffin.http

import java.net.URI

import cats.Show
import cats.syntax.all.given

import muffin.api.BackoffSettings
import muffin.model.FilePayload
import muffin.model.websocket.domain.*

trait HttpClient[F[_], -To[_], From[_]] {

  def request[In: To, Out: From](
      url: String,
      method: Method,
      body: Body[In] = Body.Empty,
      headers: Map[String, String] = Map.empty,
      params: Params => Params = identity
  ): F[Out]

  def requestRawData[In: To](
      url: String,
      method: Method,
      body: Body[In],
      headers: Map[String, String],
      params: Params => Params = identity
  ): F[Array[Byte]]

  def websocketWithListeners(
      uri: URI,
      headers: Map[String, String] = Map.empty,
      backoffSettings: BackoffSettings,
      listeners: List[EventListener[F]]
  ): F[Unit]

}

trait EventListener[F[_]] {
  def onEvent(event: Event[RawJson]): F[Unit]
}

sealed trait Body[+T]

object Body {
  case object Empty extends Body[Nothing]
  case class Json[T](value: T) extends Body[T]
  case class RawJson(value: String) extends Body[String]
  case class Multipart(parts: List[MultipartElement]) extends Body[Nothing]
}

sealed trait MultipartElement

object MultipartElement {
  case class StringElement(name: String, value: String) extends MultipartElement
  case class FileElement(name: String, value: FilePayload) extends MultipartElement
}

enum Method {
  case Get
  case Post
  case Delete
  case Put
  case Patch
}

case class Params(private val params: Map[String, String]) {
  def withParam[T: Show](name: String, value: T): Params = Params(params + (name -> value.show))

  def withParam[T: Show](name: String, value: Option[T]): Params = value.fold(this)(withParam(name, _))

  private[muffin] def mkString: String = params.map((key, value) => s"$key=$value").mkString("?", "&", "")
}

object Params {
  val Empty: Params = Params(Map.empty)
}
