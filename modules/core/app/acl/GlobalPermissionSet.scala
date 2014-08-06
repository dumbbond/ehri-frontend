package acl

import play.api.libs.json._
import defines._
import models.base.Accessor

object GlobalPermissionSet {

  // Type alias for the very verbose permission-set data structure
  type PermData = List[(String, Map[ContentTypes.Value, List[PermissionType.Value]])]
  type PermDataRaw = List[Map[String, Map[String, List[String]]]]

  /**
   * Convert the 'raw' string version of the Permission data into
   * a less stringly typed version: all the entity types and permissions
   * should all correspond to Enum values in ContentType and PermissionType.
   * 
   * NB: The Map[String, Map[String, List[String]]] items in each list are
   * only maps to make them a homogeneous data set, and there should be only
   * one key/value per map, hence we flatMap through them taking only the first
   * value in each, converting them to a tuple for internal use.
   *
   */
  private def extract(pd: PermDataRaw): PermData = {
    pd.flatMap { pmap =>
      pmap.headOption.map { case (user, perms) =>
        (user, perms.flatMap {
          case (et, plist) =>
            val perms = plist.flatMap { ps =>
              try {
                Some(PermissionType.withName(ps))
              } catch {
                case e: NoSuchElementException => None
              }
            }

            try {
              Some((ContentTypes.withName(et), perms))
            } catch {
              // If we get an expected permission, just ignore it... this makes
              // for less painful server upgrades!
              case e: NoSuchElementException => None
          }
        })
      }
    }
  }

  /**
   * Construct a new global permission set from a JSON value.
   */
  def apply(json: JsValue): GlobalPermissionSet = json.validate[List[Map[String, Map[String, List[String]]]]].fold(
    valid = { pd => new GlobalPermissionSet(extract(pd)) },
    invalid = { e => sys.error(e.toString()) }
  )
}

/**
 * Global permissions granted to either a UserProfileF or a GroupF.
 */
case class GlobalPermissionSet(data: GlobalPermissionSet.PermData)
  extends PermissionSet {

  /**
   * Check if this permission set has the given permission.
   */
  def has(sub: ContentTypes.Value, permission: PermissionType.Value): Boolean =
    !data.flatMap(_._2.get(sub)).filter( plist => plist.exists( p =>
        PermissionType.in(p, permission))).isEmpty

  /**
   * Get the permission grant for a given permission (if any), which contains
   * the accessor to whom the permission was granted.
   */
  def get[T <: Accessor](user: T, contentType: ContentTypes.Value, permission: PermissionType.Value): Option[Permission[T]] = {
    val accessors = data.flatMap {
      case (u, perms) =>
        perms.get(contentType).flatMap { permSet =>
          if (permSet.exists(p => PermissionType.in(p, permission))) Some((u, permission))
          else None
        }
    }
    // If we have one or more accessors, then we are going to
    // grant the permission, but we need to search the list
    // to determine the accessor who was granted the permissions.
    accessors.headOption.map {
      case (userId, perm) =>
        if (user.id == userId) Permission(perm)
        else {
          user.getAccessor(user.groups, userId) match {
            case Some(u) if u.id == user.id => Permission(perm)
            case s @ Some(u) => Permission(perm, s)
            case x => Permission(perm)
          }
        }
    }
  }
}
