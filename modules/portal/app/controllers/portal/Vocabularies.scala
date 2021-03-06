package controllers.portal

import controllers.AppComponents
import controllers.generic.Search
import controllers.portal.base.{Generic, PortalController}
import javax.inject.{Inject, Singleton}
import models.{Concept, Vocabulary}
import play.api.mvc.{Action, AnyContent, ControllerComponents, RequestHeader}
import services.cypher.CypherService
import services.search._
import utils.PageParams

import scala.concurrent.Future.{successful => immediate}


@Singleton
case class Vocabularies @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  cypher: CypherService,
  fc: FacetConfig
) extends PortalController
  with Generic[Vocabulary]
  with Search {

  import SearchConstants._

  private def filterKey(implicit request: RequestHeader): String = SearchConstants.HOLDER_ID

  private def filters(implicit request: RequestHeader): Map[String, Any] =
    if (!hasActiveQuery(request)) Map(SearchConstants.TOP_LEVEL -> true) else Map.empty[String, Any]

  private val portalVocabRoutes = controllers.portal.routes.Vocabularies
  private val promotedFilter = Map("fq" -> "promotionScore:[1 TO *]")

  def searchAll(params: SearchParams, paging: PageParams): Action[AnyContent] = UserBrowseAction.async { implicit request =>
    // This is a bit confusing: if there's not query we just list the promoted vocabularies.
    // If there is a query we have to fetch the promoted vocabularies and then run the search
    // of items *held by* those vocabs.
    params.query.map { q =>
      for {
        // FIXME: more efficient ways of fetching promoted vocabularies?
        parents <- findType[Vocabulary](SearchParams.empty, PageParams.empty.withoutLimit, extra = promotedFilter)
        filter = Map("fq" -> s"$HOLDER_ID:(${parents.mapItems(_._2.id).page.mkString(" ")})")
        result <- findType[Concept](params = params, paging = paging, extra = filter)
      } yield Ok(views.html.vocabulary.list(result, portalVocabRoutes.searchAll(), request.watched))
    }.getOrElse {
      findType[Vocabulary](params = params, paging = paging, extra = promotedFilter, sort = SearchSort.Name).map { result =>
        Ok(views.html.vocabulary.list(result, portalVocabRoutes.searchAll(), request.watched))
      }
    }
  }

  def browse(id: String, params: SearchParams, paging: PageParams): Action[AnyContent] = GetItemAction(id).async { implicit request =>
    if (isAjax) immediate(Ok(views.html.vocabulary.itemDetails(request.item, request.annotations, request.links, request.watched)))
    else findType[Concept](params, paging, filters = filters.updated(filterKey, id), sort = SearchSort.Name).map { result =>
      Ok(views.html.vocabulary.show(request.item, result, request.annotations,
        request.links, portalVocabRoutes.search(id), request.watched))
    }
  }

  def search(id: String, params: SearchParams, paging: PageParams, inline: Boolean): Action[AnyContent] = GetItemAction(id).async { implicit request =>
    findType[Concept](params, paging, filters = filters.updated(filterKey, id), sort = SearchSort.Name).map { result =>
      if (isAjax) {
        if (inline) Ok(views.html.common.search.inlineItemList(result, request.watched))
            .withHeaders("more" -> result.page.hasMore.toString)
        else Ok(views.html.vocabulary.childItemSearch(request.item, result,
          portalVocabRoutes.search(id), request.watched))
      }
      else Ok(views.html.vocabulary.search(request.item, result,
        portalVocabRoutes.search(id), request.watched))
    }
  }
}
