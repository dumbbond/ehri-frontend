package controllers

import defines._
import models.SystemEvent
import controllers.base.EntityRead
import play.api.libs.concurrent.Execution.Implicits._


object SystemEvents extends EntityRead[SystemEvent] {
  val entityType = EntityType.SystemEvent
  val contentType = ContentType.SystemEvent

  val builder = SystemEvent

  def get(id: String) = getAction(id) { item => annotations =>
    implicit maybeUser =>
      implicit request =>
      // In addition to the item itself, we also want to fetch the subjects associated with it.
      AsyncRest {
        rest.SystemEventDAO(maybeUser).subjectsFor(id, pageParams).map { pageOrErr =>
          pageOrErr.right.map { page =>
            Ok(views.html.systemEvents.show(SystemEvent(item), page))
          }
        }
      }
  }

  def list = listAction { page =>
    implicit maybeUser =>
      implicit request =>
        Ok(views.html.systemEvents.list(page.copy(list = page.list.map(SystemEvent(_)))))
  }
}