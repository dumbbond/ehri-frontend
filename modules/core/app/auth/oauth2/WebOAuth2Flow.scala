package auth.oauth2

import javax.inject.Inject

import auth.AuthenticationError
import auth.oauth2.providers.OAuth2Provider
import play.api.Logger
import play.api.libs.ws.WSClient

import scala.concurrent.{ExecutionContext, Future}

case class WebOAuth2Flow @Inject ()(
  ws: WSClient,
  config: play.api.Configuration
)(implicit executionContext: ExecutionContext) extends OAuth2Flow {

  private val logger = Logger(getClass)

  override def getAccessToken(provider: OAuth2Provider, handlerUrl: String, code: String): Future[OAuth2Info] = {
    val accessTokenUrl: String = provider.getAccessTokenUrl
    logger.debug(s"Fetching access token for provider ${provider.name} at $accessTokenUrl")
    ws.url(accessTokenUrl)
      .addHttpHeaders(provider.getAccessTokenHeaders: _*)
      .post(provider.getAccessTokenParams(code, handlerUrl))
      .map { r =>
      logger.trace(s"Access Data for OAuth2 ${provider.name}:-------\n${r.body}\n-----")
      provider.parseAccessInfo(r.body).getOrElse {
        throw new AuthenticationError(s"Unable to fetch access token and info for provider ${provider.name} " +
          s" via response data: ${r.body}")
      }
    }
  }

  override def getUserData(provider: OAuth2Provider, info: OAuth2Info): Future[UserData] = {
    val url: String = provider.getUserInfoUrl(info)
    val headers: Seq[(String, String)] = provider.getUserInfoHeader(info)
    logger.debug(s"Fetching info at $url with headers $headers")
    ws.url(url)
      .addQueryStringParameters(provider.getUserInfoParams(info): _*)
      .addHttpHeaders(headers: _*).get()
      .map { r =>
      logger.trace(s"User Info Data for OAuth2 ${provider.name}:-------\n${r.body}\n-----")
      provider.parseUserInfo(r.body).getOrElse{
        throw new AuthenticationError(s"Unable to fetch user info for provider ${provider.name} " +
          s" via response data: ${r.body}")
      }
    }
  }
}
