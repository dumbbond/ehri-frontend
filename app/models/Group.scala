package models

import play.api.data._
import play.api.data.Forms._

import models.base._
import defines.EntityType
import play.api.libs.json.Json
import defines.EnumWriter.enumWrites


object GroupF {

  final val BELONGS_REL = "belongsTo"

  val NAME = "name"
  val DESCRIPTION = "description"
}

case class GroupF(
  val id: Option[String],
  val identifier: String,
  val name: String,
  val description: Option[String] = None
) extends Persistable {
  val isA = EntityType.Group

  import Entity._

  def toJson = Json.obj(
    ID -> id,
    TYPE -> isA,
    DATA -> Json.obj(
      IDENTIFIER -> identifier,
      GroupF.NAME -> name,
      GroupF.DESCRIPTION -> description
    )
  )
}


object GroupForm {

  val form = Form(
    mapping(
      Entity.ID -> optional(text),
      Entity.IDENTIFIER -> nonEmptyText,
      GroupF.NAME -> nonEmptyText,
      GroupF.DESCRIPTION -> optional(nonEmptyText)
    )(GroupF.apply)(GroupF.unapply)
  )
}


case class Group(val e: Entity) extends NamedEntity with AccessibleEntity with Accessor with Formable[GroupF] {

  def to: GroupF = new GroupF(
    id = Some(e.id),
    identifier = identifier,
    name = e.stringProperty(GroupF.NAME).getOrElse(UserProfileF.PLACEHOLDER_TITLE),
    description = e.stringProperty(GroupF.DESCRIPTION)
  )
}

