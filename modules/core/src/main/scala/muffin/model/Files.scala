package muffin.model

import muffin.internal.NewType

type FileId = FileId.Type
object FileId extends NewType[String]

case class UploadFileRequest(payload: FilePayload, channelId: ChannelId)
case class UploadFileResponse(fileInfos: List[FileInfo])

case class FileInfo(id: FileId, userId: UserId, name: String)

case class FilePayload(content: Array[Byte], name: String)

object FilePayload {

  def fromBytes(content: Array[Byte]): FilePayload = FilePayload(content, "payload")

}
