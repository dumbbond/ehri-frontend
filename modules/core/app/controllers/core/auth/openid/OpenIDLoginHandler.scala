package controllers.core.auth.openid

import controllers.base.AuthController
import models.{UserProfile, Account}
import play.api.libs.openid._
import play.api.libs.concurrent.Execution.Implicits._
import play.api._
import play.api.mvc._
import concurrent.Future
import backend.{AnonymousUser, Backend}
import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc.Result
import play.api.i18n.Messages
import java.net.ConnectException
import controllers.core.auth.AccountHelpers

/**
 * OpenID login handler implementation.
 */
trait OpenIDLoginHandler extends AccountHelpers {

  self: Controller with AuthController =>

  val backend: Backend
  val globalConfig: global.GlobalConfig

  val userDAO: auth.AccountManager

  val attributes = Seq(
    "email" -> "http://schema.openid.net/contact/email",
    "axemail" -> "http://axschema.org/contact/email",
    "axname" -> "http://axschema.org/namePerson",
    "name" -> "http://openid.netdr/schema/media/spokenname",
    "firstname" -> "http://openid.net/schema/namePerson/first",
    "lastname" -> "http://openid.net/schema/namePerson/last",
    "friendly" -> "http://openid.net/schema/namePerson/friendly"
  )

  val openidForm = Form(single(
    "openid_identifier" -> nonEmptyText
  ) verifying("error.badUrl", url => utils.forms.isValidUrl(url)))

  case class OpenIDRequest[A](
    errorForm: Form[String],
    request: Request[A]
  ) extends WrappedRequest[A](request)

  protected def OpenIdLoginAction(handler: Call) = new ActionBuilder[OpenIDRequest] {
    override def invokeBlock[A](request: Request[A], block: (OpenIDRequest[A]) => Future[Result]): Future[Result] = {
      implicit val r = request
      try {
        val boundForm: Form[String] = openidForm.bindFromRequest
        boundForm.fold(
          error => {
            Logger.info("bad request " + error.toString)
            block(OpenIDRequest(error, request))
          }, openidUrl => {
            OpenID.redirectURL(
              openidUrl,
              handler.absoluteURL(globalConfig.https),
              attributes).map(url => Redirect(url))
              .recoverWith {
              case t: ConnectException =>
                Logger.warn("OpenID Login connect exception: {}", t)
                block(OpenIDRequest(boundForm
                  .withGlobalError(Messages("error.openId.url", openidUrl)), request))
              case t =>
                Logger.warn("OpenID Login argument exception: {}", t)
                block(OpenIDRequest(boundForm
                  .withGlobalError(Messages("error.openId.url", openidUrl)), request))
            }
          }
        )
      } catch {
        case _: Throwable => block(OpenIDRequest(openidForm
          .withGlobalError(Messages("error.openId")), request))
      }
    }
  }

  case class OpenIdCallbackRequest[A](
    formOrAccount: Either[Form[String],Account],
    request: Request[A]
  ) extends WrappedRequest[A](request)

  protected def OpenIdCallbackAction = new ActionBuilder[OpenIdCallbackRequest] {
    override def invokeBlock[A](request: Request[A], block: (OpenIdCallbackRequest[A]) => Future[Result]): Future[Result] = {
      implicit val r = request

      OpenID.verifiedId.flatMap { info =>

        // check if there's a user with the right id
        userDAO.openid.findByUrl(info.id).flatMap { assocOpt =>
          assocOpt.map { assoc =>
            Logger.logger.info("User '{}' logged in via OpenId", assoc.user.get.id)
            block(OpenIdCallbackRequest(Right(assoc.user.get), request))
          } getOrElse {
            val email = extractEmail(info.attributes)
              .getOrElse(sys.error("Unable to retrieve email info via OpenID"))

            val data = Map("name" -> extractName(info.attributes)
              .getOrElse(sys.error("Unable to retrieve name info via OpenID")))

            userDAO.findByEmail(email).flatMap { accountOpt =>
              accountOpt.map { account =>
                Logger.logger.info("User '{}' created OpenID association", account.id)
                for {
                  _ <- userDAO.openid.addAssociation(account, info.id)
                  r <- block(OpenIdCallbackRequest(Right(account), request))
                } yield r
              } getOrElse {
                implicit val apiUser = AnonymousUser
                for {
                  up <- backend.createNewUserProfile[UserProfile](data, groups = defaultPortalGroups)
                  account <- userDAO.create(Account(
                    id = up.id,
                    email = email.toLowerCase,
                    verified = true,
                    staff = false,
                    active = true,
                    allowMessaging = canMessage
                  ))
                  _ <- userDAO.openid.addAssociation(account, info.id)
                  r <- block(OpenIdCallbackRequest(Right(account), request))
                } yield {
                  Logger.logger.info("User '{}' created OpenID account", account.id)
                  r
                }
              }
            }
          }
        } recoverWith {
          case t => block(OpenIdCallbackRequest(
            Left(openidForm.withGlobalError("error.openId", t.getMessage)), request))
            .map(_.flashing("error" -> Messages("error.openId", t.getMessage)))
        }
      }
    }
  }

  /**
   * Pick up the email from OpenID info. This may be stored in different
   * attributes depending on the provider.
   */
  private def extractEmail(attrs: Map[String, String]): Option[String]
      = attrs.get("email").orElse(attrs.get("axemail"))

  private def extractName(attrs: Map[String,String]): Option[String] = {
    val fullName = for {
      fn <- attrs.get("firstname")
      ln <- attrs.get("lastname")
    } yield s"$fn $ln"
    attrs.get("name").orElse(attrs.get("fullname")).orElse(fullName)
  }
}