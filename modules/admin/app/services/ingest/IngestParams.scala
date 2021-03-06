package services.ingest

import defines.ContentTypes
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.Files.TemporaryFile


case class IngestParams(
  scopeType: ContentTypes.Value,
  scope: String,
  fonds: Option[String] = None,
  log: String,
  lang: Option[String] = None,
  allowUpdate: Boolean = false,
  tolerant: Boolean = false,
  handler: Option[String] = None,
  importer: Option[String] = None,
  excludes: Seq[String] = Nil,
  baseURI: Option[String] = None,
  suffix: Option[String] = None,
  file: Option[TemporaryFile] = None,
  properties: Option[TemporaryFile] = None,
  commit: Boolean = false
)

object IngestParams {
  val SCOPE_TYPE = "scope-type"
  val SCOPE = "scope"
  val FONDS = "fonds"
  val TOLERANT = "tolerant"
  val ALLOW_UPDATE = "allow-update"
  val LANG = "lang"
  val LOG = "log"
  val HANDLER = "handler"
  val IMPORTER = "importer"
  val EXCLUDES = "ex"
  val DATA_FILE = "data"
  val BASE_URI = "baseURI"
  val SUFFIX = "suffix"
  val PROPERTIES_FILE = "properties"
  val COMMIT = "commit"

  val ingestForm = Form(
    mapping(
      SCOPE_TYPE -> utils.EnumUtils.enumMapping(ContentTypes),
      SCOPE -> nonEmptyText,
      FONDS -> optional(nonEmptyText),
      LOG -> nonEmptyText,
      LANG -> optional(text),
      ALLOW_UPDATE -> boolean,
      TOLERANT -> boolean,
      HANDLER -> optional(text),
      IMPORTER -> optional(text),
      EXCLUDES -> optional(text).transform[Seq[String]](
        _.map(_.split("\n").map(_.trim).toSeq).toSeq.flatten,
        s => if(s.isEmpty) None else Some(s.mkString("\n"))),
      BASE_URI -> optional(text),
      SUFFIX -> optional(text),
      DATA_FILE -> ignored(Option.empty[TemporaryFile]),
      PROPERTIES_FILE -> ignored(Option.empty[TemporaryFile]),
      COMMIT -> default(boolean, false)
    )(IngestParams.apply)(IngestParams.unapply)
  )
}
