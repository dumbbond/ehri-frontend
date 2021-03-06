package controllers.units

import controllers.AppComponents
import controllers.base.AdminController
import controllers.generic._
import defines.{ContentTypes, EntityType, PermissionType}
import forms.VisibilityForm
import javax.inject._
import models._
import forms.FormConfigBuilder
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import services.data.DataHelpers
import services.ingest.{IngestApi, IngestParams}
import services.search._
import utils.{PageParams, RangeParams}
import views.Helpers


@Singleton
case class DocumentaryUnits @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  dataHelpers: DataHelpers
) extends AdminController
  with Read[DocumentaryUnit]
  with Visibility[DocumentaryUnit]
  with Creator[DocumentaryUnit, DocumentaryUnit]
  with Update[DocumentaryUnit]
  with Delete[DocumentaryUnit]
  with ScopePermissions[DocumentaryUnit]
  with Annotate[DocumentaryUnit]
  with Linking[DocumentaryUnit]
  with Descriptions[DocumentaryUnit]
  with AccessPoints[DocumentaryUnit]
  with Search {

  // Documentary unit facets
  import SearchConstants._

  private val entityFacets: FacetBuilder = { implicit request =>
    List(
      FieldFacetClass(
        key = IS_PARENT,
        name = Messages("facet.parent"),
        param = "parent",
        render = s => Messages("facet.parent." + s),
        sort = FacetSort.Fixed,
        display = FacetDisplay.List
      ),
      FieldFacetClass(
        key = LANGUAGE_CODE,
        name = Messages("documentaryUnit." + LANGUAGE_CODE),
        param = "lang",
        render = Helpers.languageCodeToName
      ),
      FieldFacetClass(
        key = CREATION_PROCESS,
        name = Messages("facet.source"),
        param = "source",
        render = s => Messages("facet.source." + s),
        sort = FacetSort.Name,
        display = FacetDisplay.List
      ),
      FieldFacetClass(
        key = COUNTRY_CODE,
        name = Messages("repository." + COUNTRY_CODE),
        param = "country",
        render = (s: String) => Helpers.countryCodeToName(s),
        display = FacetDisplay.DropDown
      ),
      FieldFacetClass(
        key = HOLDER_NAME,
        name = Messages("documentaryUnit.heldBy"),
        param = "holder",
        display = FacetDisplay.DropDown
      ),
      FieldFacetClass(
        key = RESTRICTED_FIELD,
        name = Messages("facet.restricted"),
        param = "restricted",
        render = s => Messages("facet.restricted." + s),
        sort = FacetSort.Fixed,
        display = FacetDisplay.List
      ),
    )
  }

  private val formConfig: FormConfigBuilder = FormConfigBuilder(EntityType.DocumentaryUnit, config)

  override protected val targetContentTypes = Seq(ContentTypes.DocumentaryUnit)

  private val form = models.DocumentaryUnit.form
  private val childForm = models.DocumentaryUnit.form
  private val descriptionForm = models.DocumentaryUnitDescription.form

  private val docRoutes = controllers.units.routes.DocumentaryUnits


  def search(params: SearchParams, paging: PageParams): Action[AnyContent] = OptionalUserAction.async { implicit request =>
    // What filters we gonna use? How about, only list stuff here that
    // has no parent items - UNLESS there's a query, in which case we're
    // going to peer INSIDE items... dodgy logic, maybe...
    val filters = if (!hasActiveQuery(request))
      Map(SearchConstants.TOP_LEVEL -> true) else Map.empty[String, Any]

    findType[DocumentaryUnit](params, paging, filters = filters, facetBuilder = entityFacets).map { result =>
      Ok(views.html.admin.documentaryUnit.search(
        result,
        docRoutes.search()))
    }
  }

  def searchChildren(id: String, params: SearchParams, paging: PageParams): Action[AnyContent] =
    ItemPermissionAction(id).async { implicit request =>
      val filterKey = if (!hasActiveQuery(request)) SearchConstants.PARENT_ID
      else SearchConstants.ANCESTOR_IDS

      findType[DocumentaryUnit](params, paging, filters = Map(filterKey -> request.item.id),
        facetBuilder = entityFacets, sort = SearchSort.Id).map { result =>
        Ok(views.html.admin.documentaryUnit.search(
          result,
          docRoutes.search()))
      }
    }

  def get(id: String, dlid: Option[String], params: SearchParams, paging: PageParams): Action[AnyContent] = ItemMetaAction(id).async { implicit request =>
    findType[DocumentaryUnit](params, paging, filters = Map(SearchConstants.PARENT_ID -> request.item.id),
      facetBuilder = entityFacets, sort = SearchSort.Id).map { result =>
      Ok(views.html.admin.documentaryUnit.show(request.item, result,
        docRoutes.get(id), request.annotations, request.links, dlid))
          .withPreferences(preferences.withRecentItem(id))
    }
  }

  def history(id: String, range: RangeParams): Action[AnyContent] = ItemHistoryAction(id, range).apply { implicit request =>
    Ok(views.html.admin.systemEvent.itemList(request.item, request.page, request.params))
  }

  def list(paging: PageParams): Action[AnyContent] = ItemPageAction(paging).apply { implicit request =>
    Ok(views.html.admin.documentaryUnit.list(request.page, request.params))
  }

  def update(id: String): Action[AnyContent] = EditAction(id).apply { implicit request =>
    Ok(views.html.admin.documentaryUnit.edit(
      request.item, form.fill(request.item.data), formConfig.forUpdate, docRoutes.updatePost(id)))
  }

  def updatePost(id: String): Action[AnyContent] = UpdateAction(id, form).apply { implicit request =>
    request.formOrItem match {
      case Left(errorForm) =>
        BadRequest(views.html.admin.documentaryUnit.edit(request.item,
          errorForm, formConfig.forUpdate, docRoutes.updatePost(id)))
      case Right(item) => Redirect(docRoutes.get(item.id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def createDoc(id: String): Action[AnyContent] = NewChildAction(id).apply { implicit request =>
    Ok(views.html.admin.documentaryUnit.create(
      request.item, childForm, formConfig.forCreate, VisibilityForm.form.fill(request.item.accessors.map(_.id)),
      request.usersAndGroups, docRoutes.createDocPost(id)))
  }

  def createDocPost(id: String): Action[AnyContent] = CreateChildAction(id, childForm).apply { implicit request =>
    request.formOrItem match {
      case Left((errorForm, accForm, usersAndGroups)) =>
        BadRequest(views.html.admin.documentaryUnit.create(request.item,
          errorForm, formConfig.forCreate, accForm, usersAndGroups,
          docRoutes.createDocPost(id)))
      case Right(doc) => Redirect(docRoutes.get(doc.id))
        .flashing("success" -> "item.create.confirmation")
    }
  }

  def createDescription(id: String): Action[AnyContent] =
    WithItemPermissionAction(id, PermissionType.Update).apply { implicit request =>
      Ok(views.html.admin.documentaryUnit.createDescription(request.item,
        form.fill(request.item.data), formConfig.forCreate, docRoutes.createDescriptionPost(id)))
    }

  def createDescriptionPost(id: String): Action[AnyContent] = UpdateAction(id, form).apply { implicit request =>
    request.formOrItem match {
      case Left(errorForm) =>
        BadRequest(views.html.admin.documentaryUnit.createDescription(request.item,
          errorForm, formConfig.forCreate, docRoutes.createDescriptionPost(id)))
      case Right(_) => Redirect(docRoutes.get(id))
        .flashing("success" -> "item.create.confirmation")
    }
  }

  def updateDescription(id: String, did: String): Action[AnyContent] = EditAction(id).apply { implicit request =>
    Ok(views.html.admin.documentaryUnit.editDescription(request.item,
      form.fill(request.item.data), formConfig.forUpdate, did, docRoutes.updateDescriptionPost(id, did)))
  }

  def updateDescriptionPost(id: String, did: String): Action[AnyContent] = UpdateAction(id, form).apply { implicit request =>
    request.formOrItem match {
      case Left(errorForm) =>
        BadRequest(views.html.admin.documentaryUnit.editDescription(request.item,
          errorForm, formConfig.forUpdate, did, docRoutes.updateDescriptionPost(id, did)))
      case Right(_) => Redirect(docRoutes.get(id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def deleteDescription(id: String, did: String): Action[AnyContent] = EditAction(id).apply { implicit request =>
    Ok(views.html.admin.documentaryUnit.deleteDescription(request.item, form.fill(request.item.data),
      did, docRoutes.deleteDescriptionPost(id, did)))
  }

  def deleteDescriptionPost(id: String, did: String): Action[AnyContent] = UpdateAction(id, form).apply { implicit request =>
    request.formOrItem match {
      case Left(errorForm) =>
        BadRequest(views.html.admin.documentaryUnit.deleteDescription(request.item,
          errorForm, did, docRoutes.deleteDescriptionPost(id, did)))
      case Right(_) => Redirect(docRoutes.get(id))
        .flashing("success" -> "item.delete.confirmation")

    }
  }

  def delete(id: String): Action[AnyContent] = CheckDeleteAction(id).apply { implicit request =>
    Ok(views.html.admin.delete(
      request.item, docRoutes.deletePost(id), docRoutes.get(id)))
  }

  def deletePost(id: String): Action[AnyContent] = DeleteAction(id).apply { implicit request =>
    Redirect(docRoutes.search())
      .flashing("success" -> "item.delete.confirmation")
  }

  def visibility(id: String): Action[AnyContent] = EditVisibilityAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.visibility(request.item,
      VisibilityForm.form.fill(request.item.accessors.map(_.id)),
      request.usersAndGroups, docRoutes.visibilityPost(id)))
  }

  def visibilityPost(id: String): Action[AnyContent] = UpdateVisibilityAction(id).apply { implicit request =>
    Redirect(docRoutes.get(id))
      .flashing("success" -> "item.update.confirmation")
  }

  def managePermissions(id: String, paging: PageParams, scopePaging: PageParams): Action[AnyContent] =
    ScopePermissionGrantAction(id, paging, scopePaging).apply { implicit request =>
      Ok(views.html.admin.permissions.manageScopedPermissions(
        request.item, request.permissionGrants, request.scopePermissionGrants,
        docRoutes.addItemPermissions(id),
        docRoutes.addScopedPermissions(id)))
    }

  def addItemPermissions(id: String): Action[AnyContent] = EditItemPermissionsAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.permissionItem(request.item, request.usersAndGroups,
      docRoutes.setItemPermissions))
  }

  def addScopedPermissions(id: String): Action[AnyContent] = EditItemPermissionsAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.permissionScope(request.item, request.usersAndGroups,
      docRoutes.setScopedPermissions))
  }

  def setItemPermissions(id: String, userType: EntityType.Value, userId: String): Action[AnyContent] =
    CheckUpdateItemPermissionsAction(id, userType, userId).apply { implicit request =>
      Ok(views.html.admin.permissions.setPermissionItem(
        request.item, request.accessor, request.itemPermissions,
        docRoutes.setItemPermissionsPost(id, userType, userId)))
    }

  def setItemPermissionsPost(id: String, userType: EntityType.Value, userId: String): Action[AnyContent] =
    UpdateItemPermissionsAction(id, userType, userId).apply { implicit request =>
      Redirect(docRoutes.managePermissions(id))
        .flashing("success" -> "item.update.confirmation")
    }

  def setScopedPermissions(id: String, userType: EntityType.Value, userId: String): Action[AnyContent] =
    CheckUpdateScopePermissionsAction(id, userType, userId).apply { implicit request =>
      Ok(views.html.admin.permissions.setPermissionScope(
        request.item, request.accessor, request.scopePermissions, targetContentTypes,
        docRoutes.setScopedPermissionsPost(id, userType, userId)))
    }

  def setScopedPermissionsPost(id: String, userType: EntityType.Value, userId: String): Action[AnyContent] =
    UpdateScopePermissionsAction(id, userType, userId).apply { implicit request =>
      Redirect(docRoutes.managePermissions(id))
        .flashing("success" -> "item.update.confirmation")
    }

  def linkTo(id: String): Action[AnyContent] =
    WithItemPermissionAction(id, PermissionType.Annotate).apply { implicit request =>
      Ok(views.html.admin.documentaryUnit.linkTo(request.item))
    }

  def linkAnnotateSelect(id: String, toType: EntityType.Value, params: SearchParams, paging: PageParams): Action[AnyContent] =
    LinkSelectAction(id, toType, params, paging).apply { implicit request =>
      Ok(views.html.admin.link.linkSourceList(
        request.item, request.searchResult, request.entityType,
        docRoutes.linkAnnotateSelect(id, toType),
        (other, copy) => docRoutes.linkAnnotate(id, toType, other, copy)))
    }

  def linkAnnotate(id: String, toType: EntityType.Value, to: String, copy: Boolean): Action[AnyContent] =
    LinkAction(id, toType, to).apply { implicit request =>
      Ok(views.html.admin.link.create(request.from, request.to,
        Link.formWithCopyOptions(copy, request.from, request.to),
          docRoutes.linkAnnotatePost(id, toType, to, copy), copy))
    }

  def linkAnnotatePost(id: String, toType: EntityType.Value, to: String, copy: Boolean): Action[AnyContent] =
    CreateLinkAction(id, toType, to, directional = copy).apply { implicit request =>
      request.formOrLink match {
        case Left((target, errorForm)) =>
          BadRequest(views.html.admin.link.create(request.from, target,
            errorForm, docRoutes.linkAnnotatePost(id, toType, to, copy), copy))
        case Right(_) =>
          Redirect(docRoutes.get(id))
            .flashing("success" -> "item.update.confirmation")
      }
    }

  def manageAccessPoints(id: String, descriptionId: String): Action[AnyContent] =
    WithDescriptionAction(id, descriptionId).apply { implicit request =>
      // Holder IDs for vocabularies and authoritative sets to which
      // access point suggestions will be constrainted. If this is empty
      // all available vocabs/auth sets will be used.
      val holders = config
        .getOptional[Seq[String]]("ehri.admin.accessPoints.holders")
        .getOrElse(Seq.empty)
      Ok(views.html.admin.documentaryUnit.editAccessPoints(request.item,
        request.description, holderIds = holders))
    }

  def ingest(id: String): Action[AnyContent] = (AdminAction andThen ItemPermissionAction(id)).apply { implicit request =>
    request.item.holder.map { scope =>
      val dataType = IngestApi.IngestDataType.EadSync
      Ok(views.html.admin.tools.ingest(scope, Some(request.item), IngestParams.ingestForm, dataType,
        controllers.admin.routes.Ingest.ingestPost(ContentTypes.DocumentaryUnit,
          scope.id, dataType, Some(id)), sync = true))
    }.getOrElse(InternalServerError(views.html.errors.fatalError()))
  }
}


