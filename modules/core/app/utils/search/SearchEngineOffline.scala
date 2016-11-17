package utils.search

import java.io.IOException

import backend.ServiceException

/**
  * Indicates that we could not reach the search engine.
  *
  * @param msg a message
  * @param cause the underlying cause
  */
case class SearchEngineOffline(msg: String, cause: Throwable) extends ServiceException(msg, cause)