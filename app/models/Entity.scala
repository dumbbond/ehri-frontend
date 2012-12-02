package models

import defines._

import play.api.libs.json.JsString
import play.api.libs.json.JsValue
import play.api.libs.json.Reads
import play.api.libs.json.Reads.JsValueReads
import play.api.libs.json.Reads.LongReads
import play.api.libs.json.Reads.StringReads
import play.api.libs.json.Reads.functorReads
import play.api.libs.json.Reads.list
import play.api.libs.json.Reads.map
import play.api.libs.json.Writes
import play.api.libs.json.Writes.JsValueWrites
import play.api.libs.json.Writes.LongWrites
import play.api.libs.json.Writes.mapWrites
import play.api.libs.json.Writes.traversableWrites
import play.api.libs.json.__
import play.api.libs.json.util.functionalCanBuildApplicative
import play.api.libs.json.util.toFunctionalBuilderOps
import play.api.libs.json.util.unlift

object Entity {
  
  val ID = "id"
  val IDENTIFIER = "identifier"
  val DATA = "data"
  val TYPE = "type"
  val RELATIONSHIPS = "relationships"  
  
  def fromString(s: String, t: EntityType.Value) = {
    new Entity("", t, Map(IDENTIFIER -> JsString(s)), Map())
  }
}

case class Entity(
  id: String,
  `type`: EntityType.Value,
  data: Map[String, JsValue] = Map(),
  relationships: Map[String, List[Entity]] = Map()) {

  def property(name: String) = data.get(name)
  
  /**
   * Shortcut for fetching a Option[String] property.
   */
  def stringProperty(name: String) = property(name).flatMap(_.asOpt[String])
  def listProperty(name: String) = property(name).flatMap(_.asOpt[List[String]]).getOrElse(List())
  def relations(s: String): List[Entity] = relationships.getOrElse(s, List())  
  def withProperty(name: String, value: JsValue) = copy(data=data + (name -> value))
  def withRelation(s: String, r: Entity) = {
    val list: List[Entity] = relationships.getOrElse(s, Nil)
    copy(relationships=relationships + (s -> (list ++ List(r))))
  }
  private val adminKeys = List(Entity.IDENTIFIER)
  def valueData: Map[String, JsValue] = {
    data.filterNot { case (k, v) => adminKeys.contains(k) }
  }

  lazy val isA: EntityType.Value = `type`
  
  override def toString() = "<%s (%d)>".format(property(Entity.IDENTIFIER), id)
}

object EntityWriter {
  import defines.EnumWriter._
  implicit val entityWrites: Writes[Entity] = (
     (__ \ Entity.ID).write[String] and
     (__ \ Entity.TYPE).write[EntityType.Type](EnumWriter.enumWrites) and
     (__ \ Entity.DATA).lazyWrite(mapWrites[JsValue]) and
     (__ \ Entity.RELATIONSHIPS).lazyWrite(
         mapWrites[List[Entity]])
  )(unlift(Entity.unapply))
}

object EntityReader {
  import defines.EnumReader
  implicit val entityReads: Reads[Entity] = (
    (__ \ Entity.ID).read[String] and
    (__ \ Entity.TYPE).read[EntityType.Type](EnumReader.enumReads(EntityType)) and
    (__ \ Entity.DATA).lazyRead(map[JsValue]) and
    (__ \ Entity.RELATIONSHIPS).lazyRead(
      map[List[Entity]](list(entityReads))))(Entity.apply _)
}
