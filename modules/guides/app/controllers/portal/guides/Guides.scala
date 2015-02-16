package controllers.portal.guides

import auth.AccountManager
import backend.Backend
import backend.rest.cypher.CypherDAO
import backend.rest.{Constants, SearchDAO}
import com.google.inject._
import controllers.generic.Search
import controllers.portal.FacetConfig
import controllers.portal.base.PortalController
import defines.EntityType
import models.GuidePage.Layout
import models.base.AnyModel
import models.{GeoCoordinates, Guide, GuidePage, _}
import play.api.Play.current
import play.api.Routes
import play.api.cache.Cached
import play.api.data.Forms._
import play.api.data._
import play.api.http.MimeTypes
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.{JsNull, JsNumber, JsString, JsValue, Json}
import play.api.mvc._
import utils._
import utils.search._
import views.html.p
import controllers.renderError

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}


@Singleton
case class Guides @Inject()(implicit globalConfig: global.GlobalConfig, searchEngine: SearchEngine, searchResolver: SearchItemResolver, backend: Backend,
                            accounts: AccountManager, pageRelocator: utils.MovedPageLookup)
  extends PortalController
  with Search
  with FacetConfig {

  val ajaxOrder = utils.search.SearchOrder.Name
  val htmlAgentOrder = utils.search.SearchOrder.Detail
  val htmlConceptOrder = utils.search.SearchOrder.ChildCount

  def jsRoutes = Cached.status(_ => "pages:guideJsRoutes", OK, 3600) {
    Action { implicit request =>
      Ok(
        Routes.javascriptRouter("jsRoutes")(
          controllers.portal.guides.routes.javascript.DocumentaryUnits.browse,
          controllers.portal.guides.routes.javascript.Guides.linkedData,
          controllers.portal.guides.routes.javascript.Guides.linkedDataInContext
        )
      ).as(MimeTypes.JAVASCRIPT)
    }
  }


  private def facetPage(page: Int, limit: Int, total: Int): (Int, Int) = ((page - 1) * limit, limit)

  /*
  *  Return SearchParams for items with hierarchy
  */
  private def getParams(request: Request[Any], eT: EntityType.Value, sort: Option[utils.search.SearchOrder.Value], isAjax: Boolean = false): SearchParams = {
    request.getQueryString("parent").map { parent =>
      SearchParams(
        query = Some(SearchConstants.PARENT_ID + ":" + parent),
        entities = List(eT),
        sort = sort
      )
    }.getOrElse {
      SearchParams(
        query = if (!isAjax) Some(SearchConstants.TOP_LEVEL + ":" + true) else None,
        entities = List(eT),
        sort = sort
      )
    }
  }

  /*
  * Return Map extras param if needed
  */
  private def mapParams(request: Map[String, Seq[String]]): (utils.search.SearchOrder.Value, Map[String, Any]) = {
    GeoCoordinates.form.bindFromRequest(request).fold(
      errorForm => SearchOrder.Name -> Map.empty,
      {
        case GeoCoordinates(lat, lng, dist) => SearchOrder.Location -> Map(
          "pt" -> s"$lat,$lng",
          "sfield" -> "location",
          "sort" -> "geodist() asc",
          "d" -> dist.getOrElse(1.0),
          "fq" -> "{!bbox}"
        )
      }
    )
  }

  /*
   *    Count Links by items
   */
  private def countLinks(virtualUnit: String, target: Seq[String]): Future[Map[String, Long]] = {
    if (target.nonEmpty) {
      val cypher = new CypherDAO
      val query = s"""
          START 
            virtualUnit = node:entities(__ID__= {inContext}),
            accessPoints = node:entities({accessPoint})
          MATCH 
               (link)-[:inContextOf]->virtualUnit,
              (doc)<-[:hasLinkTarget]-(link)-[:hasLinkTarget]->accessPoints
           WHERE doc <> accessPoints
           RETURN accessPoints.__ID__, COUNT(ID(doc))
          """.stripMargin
      val params = Map(
        "inContext" -> JsString(virtualUnit),
        "accessPoint" -> JsString(getFacetQuery(target))
      )
      cypher.cypher(query, params).map { json =>
        (json \ "data").as[List[List[JsValue]]].collect {
          case JsString(id) :: JsNumber(count) :: _ => id -> count.toLong
        }.toMap
      }
    } else {
      Future.successful(Map.empty[String, Long])
    }
  }

  /*
  *
  *   Routes functions for normal HTML
  *
  */

  private def pageNotFound = Action { implicit request =>
    NotFound(renderError("errors.pageNotFound", views.html.errors.pageNotFound()))
  }

  def itemOr404Action(f: => Option[Action[AnyContent]]): Action[AnyContent] = {
    f.getOrElse(pageNotFound)
  }

  /*
  * Return a list of guides
  */
  def listGuides() = OptionalUserAction { implicit request =>
    Ok(p.guides.guidesList(Guide.findAll(activeOnly = true)))
  }

  /*
  * Return a homepage for a guide
  */
  def home(path: String) = itemOr404Action {
    Guide.find(path, activeOnly = true).map { guide =>
      guideLayout(guide, guide.getDefaultPage)
    }
  }

  /*
  * Return a layout for a guide and a given path
  */
  def layoutRetrieval(path: String, page: String) = itemOr404Action {
    Guide.find(path, activeOnly = true).map { guide =>
      guideLayout(guide, guide.findPage(page))
    }
  }

  /*
   *    Return Ajax 
   */
  private def guideJsonItem(item: AnyModel, count: Long = 0): JsValue = {
    item match {
      case it: HistoricalAgent =>
        Json.obj(
          "name" -> Json.toJson(it.toStringLang),
          "id" -> Json.toJson(it.id),
          "type" -> Json.toJson("historicalAgent"),
          "links" -> Json.toJson(count)
        )
      case it: Concept =>
        Json.obj(
          "name" -> Json.toJson(it.toStringLang),
          "id" -> Json.toJson(it.id),
          "type" -> Json.toJson("cvocConcept"),
          "links" -> Json.toJson(count),
          "childCount" -> Json.toJson(it.childCount.getOrElse(0)),
          "parent" -> Json.toJson(it.parent match {
            case Some(p) => Json.obj(
              "name" -> Json.toJson(p.toStringLang),
              "id" -> Json.toJson(p.id)
            )
            case _ => JsNull
          }),
          "descriptions" -> Json.toJson(it.descriptions.map { case (desc) =>
            Json.toJson(Map(
              "definition" -> Json.toJson(desc.definition),
              "scopeNote" -> Json.toJson(desc.scopeNote),
              "longitude" -> Json.toJson(desc.longitude),
              "latitude" -> Json.toJson(desc.latitude)
            ))
          })
        )
      case _ => JsNull
    }
  }

  private def guideJson(page: utils.Page[(AnyModel, utils.search.SearchHit)], request: RequestHeader, links: Map[String, Long], pageParam: String = "page"): JsValue = {
    Json.obj(
      "items" -> Json.toJson(page.items.map { case (agent, hit) =>
        guideJsonItem(agent, links.getOrElse(agent.id, 0))
      }),
      "limit" -> JsNumber(page.limit),
      "page" -> JsNumber(page.page),
      "total" -> JsNumber(page.total)
    )
  }

  /*
  *
  * Link a layout [GuidePage] to a correct template function
  *
  */


  def guideLayout(guide: Guide, temp: Option[GuidePage]) = itemOr404Action {
    temp.map { page =>
      page.layout match {
        case Layout.Person => guideAuthority(page, Map(SearchConstants.HOLDER_ID -> page.content), guide)
        case Layout.Map => guideMap(page, Map(SearchConstants.HOLDER_ID -> page.content), guide)
        case Layout.Organisation => guideOrganization(page, Map(SearchConstants.HOLDER_ID -> page.content), guide)
        case Layout.Html => guideHtml(guide, page)
        case Layout.Markdown => guideMarkdown(guide, page)
        case Layout.Timeline => guideTimeline(guide, page)
      }
    }
  }


  /*
  *
  *   Layouts function, add a new function for a new type of layout
  *
  */

  /*
  *   Layout named "person" [HistoricalAgent]
  */
  def guideAuthority(page: GuidePage, params: Map[String, String], guide: Guide) = UserBrowseAction.async { implicit request =>
    for {
      r <- find[HistoricalAgent](
        filters = params,
        defaultParams = SearchParams(sort = Some(if (isAjax) ajaxOrder else htmlAgentOrder)),
        entities = List(EntityType.HistoricalAgent)
      )
      links <- countLinks(guide.virtualUnit, r.page.items.map { case (item, hit) => item.id})
    } yield render {
      case Accepts.Html() =>
        if (isAjax) Ok(p.guides.ajax(guide, page, r.page, r.params, links))
        else Ok(p.guides.person(guide, page, guide.findPages(), r.page, r.params, links))
      case Accepts.Json() =>
        Ok(guideJson(r.page, request, links))
    }
  }

  /*
  *   Layout named "map" [Concept]
  */
  def guideMap(page: GuidePage, params: Map[String, String], guide: Guide) = UserBrowseAction.async { implicit request =>
    mapParams(
      if (request.queryString.contains("lat") && request.queryString.contains("lng")) request.queryString
      else page.getParams
    ) match {
      case (sort, geoloc) => for {
        r <- find[Concept](
          params,
          extra = geoloc,
          defaultParams = SearchParams(sort = Some(sort), count = Some(500)),
          entities = List(EntityType.Concept),
          facetBuilder = conceptFacets)
        links <- countLinks(guide.virtualUnit, r.page.items.map { case (item, hit) => item.id})
      } yield render {
          case Accepts.Html() =>
            if (isAjax) Ok(p.guides.ajax(guide, page, r.page, r.params, links))
            else Ok(p.guides.places(guide, page, guide.findPages(), r.page, r.params, links, guideJson(r.page, request, links)))
          case Accepts.Json() =>
            Ok(guideJson(r.page, request, links))
        }
    }
  }

  /*
   *   Layout named "organisation" [Concept]
   */
  def guideOrganization(page: GuidePage, params: Map[String, String], guide: Guide) = UserBrowseAction.async { implicit request =>
    for {
      r <- find[Concept](
        params,
        defaultParams = getParams(request, EntityType.Concept, Some(if (isAjax) ajaxOrder else htmlConceptOrder), isAjax = isAjax),
        facetBuilder = conceptFacets
      )
      links <- countLinks(guide.virtualUnit, r.page.items.map { case (item, hit) => item.id})
    } yield render {
      case Accepts.Html() =>
        if (isAjax) Ok(p.guides.ajax(guide, page, r.page, r.params, links))
        else Ok(p.guides.organisation(guide, page, guide.findPages(), r.page, r.params, links))
      case Accepts.Json() =>
        Ok(guideJson(r.page, request, links))
    }
  }

  /*
   *   Layout named "html" (Html)
   */
  def guideHtml(guide: Guide, page: GuidePage) = OptionalUserAction { implicit request =>
    Ok(p.guides.html(guide, page))
  }

  /*
   *   Layout named "html" (Html)
   */
  def guideMarkdown(guide: Guide, page: GuidePage) = OptionalUserAction { implicit request =>
    Ok(p.guides.markdown(guide, page))
  }

  /**
   * Layout named "timeline"
   */
  def guideTimeline(guide: Guide, page: GuidePage) = OptionalUserAction { implicit request =>
    Ok(p.guides.timeline(guide, page))
  }

  /*
   * Form for browse
   */
  private val facetsForm = Form(
    tuple(
      "kw" -> list(text),
      PageParams.PAGE_PARAM -> default(number, 1),
      Constants.LIMIT_PARAM -> default(number, 10)
    )
  )

  private def getFacetQuery(ids: Seq[String]): String = {
    ids.filterNot(_.isEmpty).map("__ID__:" + _).reduce((a, b) => a + " OR " + b)
  }

  /*
   *   Faceted request
   */
  private def searchFacets(guide: Guide, ids: List[String]): Future[Seq[Long]] = {
    val cypher = new CypherDAO
    val query =
      s"""
        START 
          virtualUnit = node:entities(__ID__= {guide}), 
          accessPoints = node:entities({guideFacets})
        MATCH 
             (link)-[:inContextOf]->virtualUnit,
            (doc)<-[:hasLinkTarget]-(link)-[:hasLinkTarget]->accessPoints
         WHERE doc.__ISA__ = "documentaryUnit"
         WITH collect(accessPoints.__ID__) AS accessPointsId, doc
         WHERE ALL (x IN {accesslist} 
                   WHERE x IN accessPointsId)
         RETURN ID(doc)
        """.stripMargin
    cypher.cypher(query, Map(
      /* All IDS */
      "guide" -> JsString(guide.virtualUnit),
      "accesslist" -> Json.toJson(ids),
      "guideFacets" -> JsString(getFacetQuery(ids))
      /* End IDS */
    )).map { r =>
      (r \ "data").as[Seq[Seq[Long]]].flatten
    }
  }

  /*
   * Function to get items
   */
  private def otherFacets(guide: Guide, ids: Seq[Long]): Future[Seq[Long]] = {
    val cypher = new CypherDAO
    val query =
      s"""
        START 
          virtualUnit = node:entities(__ID__= {guide}), 
          doc = node({docList})
        MATCH 
             (link)-[:inContextOf]->virtualUnit,
            doc<-[:hasLinkTarget]-(link)-[:hasLinkTarget]->(accessPoints)
          WHERE doc <> accessPoints
         RETURN DISTINCT ID(accessPoints)
        """.stripMargin
    cypher.cypher(query, Map(
      /* All IDS */
      "guide" -> JsString(guide.virtualUnit),
      "docList" -> Json.toJson(ids)
      /* End IDS */
    )).map { r =>
      (r \ "data").as[Seq[Seq[Long]]].flatten
    }
  }

  private def facetSlice(ids: Seq[Long], page: Int, limit: Int): Seq[Long] = {
    val pages = facetPage(page, limit, ids.size)
    ids.slice(pages._1, pages._2)
  }

  private def pagify(docsId: Seq[Long], docsItems: Seq[DocumentaryUnit], accessPoints: Seq[AnyModel], page: Int, limit: Int): SearchResult[DocumentaryUnit] = {
    facetPage(page, limit, docsId.size) match {
      case (start, end) => SearchResult(
        Page(
          items = docsItems,
          offset = start,
          limit = end - start,
          total = docsId.size
        ),
        SearchParams.empty,
        facetClasses = List(
          FieldFacetClass(
            param = "kw[]",
            name = "Keyword",
            key = "kw",
            facets = accessPoints.map { ap =>
              FieldFacet(value = ap.id, name = Some(ap.toStringLang), applied = true, count = 1)
            }
          )
        )
      )
    }
  }

  private def mapAccessPoints(guide: Guide, facets: Seq[AnyModel]): Map[String, Seq[AnyModel]] = {
    guide.findPages().map { page =>
      page.content -> facets.collect {
        case f: Concept if f.vocabulary.exists(_.id == page.content) => f
        case f: HistoricalAgent if f.set.exists(_.id == page.content) => f
      }
    }.toMap
  }

  /*
  *   Faceted search
  */
  def guideFacets(path: String) = OptionalUserAction.async { implicit request =>
    Guide.find(path, activeOnly = true).map { guide =>
      /*
       *  If we have keyword, we make a query 
       */
      val defaultResult = Ok(p.guides.facet(guide, GuidePage.faceted, guide.findPages(), SearchResult.empty, Map().empty))
      facetsForm.bindFromRequest.fold(
      errs => immediate(defaultResult), {
        case (selectedFacets, page, limit) if selectedFacets.filterNot(_.isEmpty).nonEmpty => for {
          ids <- searchFacets(guide, selectedFacets.filterNot(_.isEmpty))
          docs <- SearchDAO.listByGid[DocumentaryUnit](facetSlice(ids, page, limit))
          selectedAccessPoints <- SearchDAO.list[AnyModel](selectedFacets.filterNot(_.isEmpty))
          availableFacets <- otherFacets(guide, ids)
          tempAccessPoints <- SearchDAO.listByGid[AnyModel](availableFacets)
        } yield {
          Ok(p.guides.facet(guide, GuidePage.faceted, guide.findPages(), pagify(ids, docs, selectedAccessPoints, page, limit), mapAccessPoints(guide, tempAccessPoints)))
        }
        case _ => immediate(defaultResult)
      }
      )
    } getOrElse {
      immediate(NotFound(renderError("errors.itemNotFound", views.html.errors.itemNotFound(Some(path)))))
    }
  }

  private val searchLinksForm = Form(
    single(
      "type" -> optional(text
        .verifying(
          "NoTypeGiven",
          c => EntityType.values.map(v => v.toString).contains(c)
        )
      )
    )
  )


  private def searchLinks(target: String, documentType: String = EntityType.DocumentaryUnit.toString, context: Option[String] = None): Future[Seq[Long]] = {
    val cypher = new CypherDAO
    context match {
      case Some(str) =>
        val query = s"""
          |START
          |  virtualUnit = node:entities(__ID__= {inContext}),
          |  accessPoints = node:entities(__ID__= {accessPoint})
          |MATCH
          |     (link)-[:inContextOf]->virtualUnit,
          |    (doc)<-[:hasLinkTarget]-(link)-[:hasLinkTarget]->accessPoints
          |WHERE doc <> accessPoints AND doc.__ISA__ = {type}
          |RETURN ID(doc) LIMIT 5
        """.stripMargin
        val params = Map(
          "inContext" -> JsString(str),
          "accessPoint" -> JsString(target),
          "type" -> JsString(documentType)
        )
        cypher.cypher(query, params).map { r =>
          (r \ "data").as[Seq[Seq[Long]]].flatten
        }
      case _ =>
        val query: String = s"""
          |START
          |  accessPoints = node:entities(__ID__= {accessPoint})
          |MATCH
          |     (doc)<-[:hasLinkTarget]-(link)-[:hasLinkTarget]->accessPoints
          | WHERE doc <> accessPoints AND doc.__ISA__ = {type}
          | RETURN ID(doc) LIMIT 5
        """.stripMargin
        val params = Map(
          "accessPoint" -> JsString(target),
          "type" -> JsString(documentType)
        )
        cypher.cypher(query, params).map { r =>
          (r \ "data").as[Seq[Seq[Long]]].flatten
        }
    }
  }

  def linkedData(id: String) = UserBrowseAction.async { implicit request =>
    for {
      ids <- searchLinksForm.bindFromRequest(request.queryString).fold(
      errs => searchLinks(id), {
        case Some(t) => searchLinks(id, t)
        case _ => searchLinks(id)
      })
      docs <- SearchDAO.listByGid[AnyModel](ids)
    } yield Ok(Json.toJson(docs.zip(ids).map { case (doc, gid) =>
      Json.toJson(FilterHit(doc.id, "", doc.toStringLang, doc.isA, None, gid))
    }))
  }

  def linkedDataInContext(id: String, context: String) = UserBrowseAction.async { implicit request =>
    for {
      ids <- searchLinksForm.bindFromRequest(request.queryString).fold(
      errs => searchLinks(id, context = Some(context)), {
        case Some(t) => searchLinks(id, t, Some(context))
        case _ => searchLinks(id, context = Some(context))
      })
      docs <- SearchDAO.listByGid[AnyModel](ids)
    } yield Ok(Json.toJson(docs.zip(ids).map { case (doc, gid) =>
      Json.toJson(FilterHit(doc.id, "", doc.toStringLang, doc.isA, None, gid))
    }))
  }
}