package rest

import models.json.{RestConvertable, RestResource, RestReadable}
import scala.concurrent.Future
import utils.{SystemEventParams, ListParams, PageParams}
import models._
import models.base.{Accessor, AnyModel}
import acl.{ItemPermissionSet, GlobalPermissionSet}
import defines.{EntityType, ContentTypes}
import play.api.libs.json.JsObject

/**
  * @author Mike Bryant (http://github.com/mikesname)
  */
class RestBackend()(implicit eventHandler: RestEventHandler) extends Backend {

  private val generic = new EntityDAO
  private val descriptions = new DescriptionDAO
  private val perms = new PermissionDAO
  private val links = new LinkDAO
  private val annotations = new AnnotationDAO
  private val events = new SystemEventDAO
  private val visibility = new VisibilityDAO

   // Generic CRUD
   def get[MT](entityType: EntityType.Value, id: String)(implicit apiUser: ApiUser, rd: RestReadable[MT]): Future[Either[RestError, MT]]
      = generic.get(entityType, id)

   def get[MT](id: String)(implicit apiUser: ApiUser, rs: RestResource[MT], rd: RestReadable[MT]): Future[Either[RestError, MT]]
      = generic.get(id)

   def getJson[MT](id: String)(implicit apiUser: ApiUser, rs: RestResource[MT]): Future[Either[RestError, JsObject]]
      = generic.getJson(id)

   def get[MT](key: String, value: String)(implicit apiUser: ApiUser, rs: RestResource[MT], rd: RestReadable[MT]): Future[Either[RestError, MT]]
      = generic.get(key, value)

   def create[MT,T](item: T, accessors: List[String] = Nil,params: Map[String,Seq[String]] = Map(), logMsg: Option[String] = None)(implicit apiUser: ApiUser, rs: RestResource[MT], wrt: RestConvertable[T], rd: RestReadable[MT]): Future[Either[RestError, MT]]
      = generic.create(item, accessors, params, logMsg)

   def createInContext[MT,T,TT](id: String, contentType: ContentTypes.Value, item: T, accessors: List[String] = Nil, logMsg: Option[String] = None)(implicit apiUser: ApiUser, wrt: RestConvertable[T], rs: RestResource[MT], rd: RestReadable[TT]): Future[Either[RestError, TT]]
      = generic.createInContext(id, contentType, item, accessors, logMsg)

   def update[MT,T](id: String, item: T, logMsg: Option[String] = None)(implicit apiUser: ApiUser, wrt: RestConvertable[T], rs: RestResource[MT], rd: RestReadable[MT]): Future[Either[RestError, MT]]
      = generic.update(id, item, logMsg)

   def delete[MT](entityType: EntityType.Value, id: String, logMsg: Option[String] = None)(implicit apiUser: ApiUser): Future[Either[RestError, Boolean]]
      = generic.delete(entityType, id, logMsg)

   // Descriptions
   def createDescription[MT,DT](id: String, item: DT, logMsg: Option[String] = None)(implicit apiUser: ApiUser, rs: RestResource[MT], fmt: RestConvertable[DT], rd: RestReadable[MT]): Future[Either[RestError, MT]]
      = descriptions.createDescription(id, item, logMsg)

   def updateDescription[MT,DT](id: String, did: String, item: DT, logMsg: Option[String] = None)(implicit apiUser: ApiUser, rs: RestResource[MT], fmt: RestConvertable[DT], rd: RestReadable[MT]): Future[Either[RestError, MT]]
      = descriptions.updateDescription(id, did, item, logMsg)

   def deleteDescription[MT](id: String, did: String, logMsg: Option[String] = None)(implicit apiUser: ApiUser, rs: RestResource[MT], rd: RestReadable[MT]): Future[Either[RestError, Boolean]]
      = descriptions.deleteDescription(id, did, logMsg)

   def createAccessPoint[MT,DT](id: String, did: String, item: DT, logMsg: Option[String] = None)(implicit apiUser: ApiUser, rs: RestResource[MT], fmt: RestConvertable[DT], rd: RestReadable[MT]): Future[Either[RestError, (MT,DT)]]
      = descriptions.createAccessPoint(id, did, item, logMsg)

   def deleteAccessPoint[MT <: AnyModel](id: String, did: String, apid: String, logMsg: Option[String] = None)(implicit apiUser: ApiUser, rs: RestResource[MT], rd: RestReadable[MT]): Future[Either[RestError, MT]]
      = descriptions.deleteAccessPoint(id, did, apid, logMsg)

   // Annotations
   def getAnnotationsForItem(id: String)(implicit apiUser: ApiUser): Future[Either[RestError, Map[String,List[Annotation]]]]
      = annotations.getFor(id)

   def createAnnotation(id: String, ann: AnnotationF)(implicit apiUser: ApiUser): Future[Either[RestError, Annotation]]
      = annotations.create(id, ann)

   // Links
   def getLinksForItem(id: String)(implicit apiUser: ApiUser): Future[Either[RestError, List[Link]]]
      = links.getFor(id)

   def linkItems(id: String, src: String, link: LinkF, accessPoint: Option[String] = None)(implicit apiUser: ApiUser): Future[Either[RestError, Link]]
      = links.link(id, src, link, accessPoint)

   def deleteLink(id: String, linkId: String)(implicit apiUser: ApiUser): Future[Either[RestError,Boolean]]
      = links.deleteLink(id, linkId)

   def deleteAccessPoint(id: String)(implicit apiUser: ApiUser): Future[Either[RestError,Boolean]]
      = links.deleteAccessPoint(id)

   def linkMultiple(id: String, srcToLinks: List[(String,LinkF,Option[String])])(implicit apiUser: ApiUser): Future[Either[RestError, List[Link]]]
      = links.linkMultiple(id, srcToLinks)

   // Permissions
   def getItemPermissions[T <: Accessor](user: T, contentType: ContentTypes.Value, id: String)(implicit apiUser: ApiUser): Future[Either[RestError, ItemPermissionSet[T]]]
      = perms.getItem(user, contentType, id)

   def setItemPermissions[T <: Accessor](user: T, contentType: ContentTypes.Value, id: String, data: List[String])(implicit apiUser: ApiUser): Future[Either[RestError, ItemPermissionSet[T]]]
      = perms.setItem(user, contentType, id, data)

   def setGlobalPermissions[T <: Accessor](user: T, data: Map[String, List[String]])(implicit apiUser: ApiUser): Future[Either[RestError, GlobalPermissionSet[T]]]
      = perms.set(user, data)

   def getGlobalPermissions[T <: Accessor](user: T)(implicit apiUser: ApiUser): Future[Either[RestError, GlobalPermissionSet[T]]]
      = perms.get(user)

   def getScopePermissions[T <: Accessor](user: T, id: String)(implicit apiUser: ApiUser): Future[Either[RestError, GlobalPermissionSet[T]]]
      = perms.getScope(user, id)

   def setScopePermissions[T <: Accessor](user: T, id: String, data: Map[String,List[String]])(implicit apiUser: ApiUser): Future[Either[RestError, GlobalPermissionSet[T]]]
      = perms.setScope(user, id, data)

   def listPermissionGrants[T <: Accessor](user: T, params: PageParams)(implicit apiUser: ApiUser): Future[Either[RestError, Page[PermissionGrant]]]
      = perms.list(user, params)

   def listItemPermissionGrants(id: String, params: PageParams)(implicit apiUser: ApiUser): Future[Either[RestError, Page[PermissionGrant]]]
      = perms.listForItem(id, params)

   def listScopePermissionGrants(id: String, params: PageParams)(implicit apiUser: ApiUser): Future[Either[RestError, Page[PermissionGrant]]]
      = perms.listForScope(id, params)

   def addGroup(groupId: String, userId: String)(implicit apiUser: ApiUser): Future[Either[RestError, Boolean]]
      = perms.addGroup(groupId, userId)

   def removeGroup(groupId: String, userId: String)(implicit apiUser: ApiUser): Future[Either[RestError, Boolean]]
      = perms.removeGroup(groupId, userId)

   // Events
   def subjectsForEvent(id: String, params: PageParams)(implicit apiUser: ApiUser): Future[Either[RestError, Page[AnyModel]]]
      = events.subjectsFor(id, params)

   def listEvents(params: ListParams, filters: SystemEventParams)(implicit apiUser: ApiUser): Future[Either[RestError, List[SystemEvent]]]
      = events.list(params, filters)

   def history(id: String, params: PageParams)(implicit apiUser: ApiUser): Future[Either[RestError, Page[SystemEvent]]]
      = events.history(id, params)

   // Visibility
   def setVisibility[MT](id: String, data: List[String])(implicit apiUser: ApiUser, rd: RestReadable[MT]): Future[Either[RestError, MT]]
      = visibility.set(id, data)
 }