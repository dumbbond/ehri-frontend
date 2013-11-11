package controllers.generic

import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._
import models._
import models.json.{RestReadable, ClientConvertable}
import utils.{ListParams, PageParams}

import scala.concurrent.Future

/**
 * Controller trait which handles the listing and showing of Entities that
 * implement the AccessibleEntity trait.
 *
 * @tparam MT Meta-model
 */
trait Read[MT] extends Generic[MT] {
  val DEFAULT_LIMIT = 20

  val defaultPage: PageParams = new PageParams()
  val defaultChildPage: PageParams = new PageParams()

  object getEntity {
    def async(id: String, user: Option[UserProfile])(f: MT => Future[SimpleResult])(
        implicit rd: RestReadable[MT], userOpt: Option[UserProfile], request: RequestHeader): Future[SimpleResult] = {
      backend.get(id).flatMap { item =>
        f(item)
      }
    }

    def apply(id: String, user: Option[UserProfile])(f: MT => SimpleResult)(
        implicit rd: RestReadable[MT], userOpt: Option[UserProfile], request: RequestHeader): Future[SimpleResult] = {
      async(id, user)(f.andThen(t => Future.successful(t)))
    }
  }

  object getEntityT {
    def async[T](otherType: defines.EntityType.Type, id: String)(f: T => Future[SimpleResult])(
        implicit userOpt: Option[UserProfile], request: RequestHeader, rd: RestReadable[T]): Future[SimpleResult] = {
      backend.get[T](otherType, id).flatMap { item =>
        f(item)
      }
    }
    def apply[T](otherType: defines.EntityType.Type, id: String)(f: T => SimpleResult)(
      implicit rd: RestReadable[T], userOpt: Option[UserProfile], request: RequestHeader): Future[SimpleResult] = {
      async(otherType, id)(f.andThen(t => Future.successful(t)))
    }
  }

  object getAction {
    def async(id: String)(f: MT => Map[String,List[Annotation]] => List[Link] => Option[UserProfile] => Request[AnyContent] => Future[SimpleResult])(
        implicit rd: RestReadable[MT], crd: ClientConvertable[MT]) = {
      itemPermissionAction.async[MT](contentType, id) { item => implicit maybeUser => implicit request =>
          // NB: Effectively disable paging here by using a high limit
        val annsReq = backend.getAnnotationsForItem(id)
        val linkReq = backend.getLinksForItem(id)
        for {
          anns <- annsReq
          links <- linkReq
          r <- f(item)(anns)(links)(maybeUser)(request)
        } yield r
      }
    }

    def apply(id: String)(f: MT => Map[String,List[Annotation]] => List[Link] => Option[UserProfile] => Request[AnyContent] => SimpleResult)(
      implicit rd: RestReadable[MT], crd: ClientConvertable[MT]) = {
      async(id)(f.andThen(_.andThen(_.andThen(_.andThen(_.andThen(t => Future.successful(t)))))))
    }
  }

  def getWithChildrenAction[CT](id: String)(
      f: MT => rest.Page[CT] => PageParams =>  Map[String,List[Annotation]] => List[Link] => Option[UserProfile] => Request[AnyContent] => SimpleResult)(
          implicit rd: RestReadable[MT], crd: RestReadable[CT], cfmt: ClientConvertable[MT]) = {
    itemPermissionAction.async[MT](contentType, id) { item => implicit userOpt => implicit request =>
      val params = PageParams.fromRequest(request)
      for {
        anns <- backend.getAnnotationsForItem(id)
        children <- backend.pageChildren[MT,CT](id, params)
        links <- backend.getLinksForItem(id)
      } yield f(item)(children)(params)(anns)(links)(userOpt)(request)
    }
  }

  def pageAction(f: rest.Page[MT] => PageParams => Option[UserProfile] => Request[AnyContent] => SimpleResult)(
      implicit rd: RestReadable[MT], cfmt: ClientConvertable[MT]) = {
    userProfileAction.async { implicit userOpt => implicit request =>
      val params = PageParams.fromRequest(request)
      backend.page(params).map { page =>
        f(page)(params)(userOpt)(request)
      }
    }
  }

  def listAction(f: List[MT] => ListParams => Option[UserProfile] => Request[AnyContent] => SimpleResult)(
    implicit rd: RestReadable[MT], cfmt: ClientConvertable[MT]) = {
    userProfileAction.async { implicit userOpt => implicit request =>
      val params = ListParams.fromRequest(request)
      backend.list(params).map { list =>
        f(list)(params)(userOpt)(request)
      }
    }
  }

  def historyAction(id: String)(
      f: MT => rest.Page[SystemEvent] => PageParams => Option[UserProfile] => Request[AnyContent] => SimpleResult)(implicit rd: RestReadable[MT]) = {
    userProfileAction.async { implicit userOpt => implicit request =>
      val params = PageParams.fromRequest(request)
      for {
        item <- backend.get(id)
        events <- backend.history(id, params)
      } yield f(item)(events)(params)(userOpt)(request)
    }
  }
}