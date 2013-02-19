package controllers

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._

import base.{ControllerHelpers, AuthController}
import play.api.data.{FormError, Form}
import play.api.data.Forms._
import defines.{EntityType, PermissionType, ContentType}
import play.api.i18n.Messages
import org.mindrot.jbcrypt.BCrypt
import models.UserProfileF
import models.sql.OpenIDUser


object Admin extends Controller with AuthController with ControllerHelpers {

  val userPasswordForm = Form(
    tuple(
      "email" -> email,
      "username" -> nonEmptyText,
      "name" -> nonEmptyText,
      "password" -> nonEmptyText,
      "confirm" -> nonEmptyText
    ) verifying(Messages("admin.passwordsDoNotMatch"), f => f match {
      case (_, _, _, pw, pwc) => pw == pwc
    })
  )

  val groupMembershipForm = Form(single("group" -> list(nonEmptyText)))

  def adminActions = adminAction { implicit userOpt => implicit request =>
    Ok(views.html.admin.actions())
  }

  def createUser = withContentPermission(PermissionType.Create, ContentType.UserProfile) { implicit userOpt => implicit request =>
    getGroups { groups =>
      Ok(views.html.admin.createUser(userPasswordForm, groupMembershipForm, groups, routes.Admin.createUserPost))
    }
  }

  def createUserPost = withContentPermission(PermissionType.Create, ContentType.UserProfile) { implicit userOpt => implicit request =>
    // TODO: Refactor to make this logic clearer...

    // Fix Play's maddening handling of multi-select values
    val boundGroupForm = groupMembershipForm.bindFromRequest(
        fixMultiSelects(request.body.asFormUrlEncoded, "group"))

    userPasswordForm.bindFromRequest.fold(
      errorForm => {
        getGroups { groups =>
          Ok(views.html.admin.createUser(errorForm, boundGroupForm,
              groups, routes.Admin.createUserPost))
        }
      },
      values => {
        val (email, username, name, pw, _) = values
        // check if the email is already registered...
        models.sql.OpenIDUser.findByEmail(email).map { account =>
          val errForm = userPasswordForm.bindFromRequest
            .withError(FormError("email", Messages("admin.userEmailAlreadyRegistered", account.profile_id)))
          getGroups { groups =>
            BadRequest(views.html.admin.createUser(errForm, boundGroupForm,
                groups, routes.Admin.createUserPost))
          }
        } getOrElse {
          // It's not registered, so create the account...
          val user = UserProfileF(id=None, identifier=username, name=name,
            location=None, about=None, languages=None)
          val groups = boundGroupForm.value.getOrElse(List())

          AsyncRest {
            rest.EntityDAO(EntityType.UserProfile, userOpt)
                .create(user, params = Map("group" -> groups)).map { itemOrErr =>
              itemOrErr.right.map { entity =>
                models.sql.OpenIDUser.create(email, entity.id).map { account =>
                  account.setPassword(BCrypt.hashpw(pw, BCrypt.gensalt))
                  Redirect(routes.UserProfiles.get(entity.id))
                }.getOrElse {
                  // FIXME: Handle this - probably by throwing a global error.
                  // If it fails it'll probably die anyway...
                  BadRequest("creating user account failed!")
                }
              }
            }
          }
        }
      }
    )
  }

  def passwordLogin = Action { implicit request =>
    val form = Form(
      tuple(
        "email" -> email,
        "password" -> nonEmptyText
      )
    )
    Ok(views.html.pwLogin(form, routes.Admin.passwordLoginPost))
  }

  def passwordLoginPost = Action { implicit request =>
    val form = Form(
      tuple(
        "email" -> email,
        "password" -> nonEmptyText
      )
    ).bindFromRequest
    val action = routes.Admin.passwordLoginPost

    form.fold(
      errorForm => {
        BadRequest(views.html.pwLogin(errorForm, action))
      },
      data => {
        val (email, pw) = data
        OpenIDUser.findByEmail(email).flatMap { acc =>
          acc.password.flatMap { hashed =>
            if (BCrypt.checkpw(pw, hashed)) {
              Some(Application.gotoLoginSucceeded(acc.profile_id))
            } else {
              None
            }
          }
        } getOrElse {
          Redirect(routes.Admin.passwordLogin).flashing("error" -> Messages("login.badUsernameOrPassword"))
        }
      }
    )
  }

  //
  // Allow a logged-in user to change their account password.
  //
  def changePassword = TODO

  def changePasswordPost = TODO
}