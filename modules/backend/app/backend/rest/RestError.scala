package backend.rest

import backend.ErrorSet
import play.api.data.validation.{ValidationError => PlayValidationError}
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.mvc.RequestHeader

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
sealed trait RestError extends Throwable

case class PermissionDenied(
  user: Option[String] = None,
  permission: Option[String] = None,
  item: Option[String] = None,
  scope: Option[String] = None
) extends RuntimeException(s"Permission denied on $item for $user") with RestError

case class ValidationError(errorSet: ErrorSet) extends RuntimeException(errorSet.toString) with RestError

case class BadRequest(msg: String) extends RuntimeException(msg) with RestError

case class DeserializationError() extends RestError

case class IntegrityError() extends RestError

case class ItemNotFound(
  key: Option[String] = None,
  value: Option[String] = None,
  message: Option[String] = None
) extends RuntimeException(message.getOrElse("No further info")) with RestError

case class ServerError(error: String) extends RuntimeException(error) with RestError

case class CriticalError(error: String) extends RuntimeException(error) with RestError

case class BadJson(
  error: Seq[(JsPath,Seq[PlayValidationError])],
  url: Option[String] = None,
  data: Option[String] = None
) extends RestError {
  def prettyError = Json.prettyPrint(JsError.toFlatJson(error))
  override def getMessage = url match {
    case Some(path) => s"""Parsing error from data at $path
        |
        |Data:
        |
        |${data.getOrElse("No data available")}
        |
        |Error:
        |
        |$prettyError
      """.stripMargin
    case None => s"Parsing error (no context): $prettyError"
  }

  def getMessageWithContext(request: RequestHeader): String =
    s"Error at ${request.path}: $getMessage"
}

object ItemNotFound {
  val itemNotFoundReads: Reads[ItemNotFound] = (
    (__ \ "details" \ "key").readNullable[String] and
      (__ \ "details" \ "value").readNullable[String] and
      (__ \ "details" \ "message").readNullable[String]
    )(ItemNotFound.apply _)

  val itemNotFoundWrites: Writes[ItemNotFound] = (
    (__ \ "key").writeNullable[String] and
      (__ \ "value").writeNullable[String] and
      (__ \ "message").writeNullable[String]
    )(unlift(ItemNotFound.unapply))

  implicit val itemNotFoundFormat = Format(itemNotFoundReads, itemNotFoundWrites)
}


object PermissionDenied {
  val permissionDeniedReads: Reads[PermissionDenied] = (
    (__ \ "details" \ "accessor").readNullable[String] and
      (__ \ "details" \ "permission").readNullable[String] and
      (__ \ "details" \ "item").readNullable[String] and
      (__ \ "details" \ "scope").readNullable[String]
    )(PermissionDenied.apply _)

  val permissionDeniedWrites: Writes[PermissionDenied] = (
    (__ \ "accessor").writeNullable[String] and
      (__ \ "permission").writeNullable[String] and
      (__ \ "item").writeNullable[String] and
      (__ \ "scope").writeNullable[String]
    )(unlift(PermissionDenied.unapply))

  implicit val permissionDeniedFormat = Format(
    permissionDeniedReads, permissionDeniedWrites)
}
