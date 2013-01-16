package rest

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import play.api.libs.ws.WS
import models.UserProfile
import play.api.libs.ws.Response
import play.api.mvc.Headers

case class ApiDAO(val userProfile: Option[UserProfile]) extends RestDAO {

  def requestUrl = "http://%s:%d/%s".format(host, port, mount)

  def authHeaders: Map[String, String] = userProfile match {
    case Some(up) => (headers + (AUTH_HEADER_NAME -> up.id))
    case None => headers
  }

  def get(urlpart: String, headers: Headers): Future[Response] = {
    WS.url(enc(requestUrl, urlpart))
    	.withHeaders(authHeaders.toSeq: _*).get
  }
}