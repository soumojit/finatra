package com.twitter.finatra.http.request

import com.twitter.finagle.http.{Fields, Request}
import com.twitter.finatra.http.exceptions.{BadRequestException, NotAcceptableException}
import com.twitter.finatra.http.fileupload.{FinagleRequestFileUpload, MultipartItem}
import com.twitter.inject.conversions.string._
import java.net.URI

object RequestUtils {

  /** Fully qualified requested URL with ending slash and no query params (suitable for location header creation) */
  def pathUrl(request: Request): String = {
    val scheme = getScheme(request)
    val hostHeader = getHost(request)

    val pathWithTrailingSlash = if (request.path.endsWith("/")) request.path else request.path + "/"
    scheme + "://" + hostHeader + pathWithTrailingSlash
  }

  /** Multipart parsed params */
  def multiParams(request: Request): Map[String, MultipartItem] = {
    new FinagleRequestFileUpload().parseMultipartItems(request)
  }

  /**
   * Content Negotiation
   * Example Accept Header format :
   * Accept: text/plain; q=0.5, text/html,
   * text/html; q=0.8, application/json
   */
  def respondTo[T](request: Request)(callback: PartialFunction[ContentType, T]): T = {
    val acceptHeader = request.headerMap.getOrElse(Fields.Accept, "*/*")
    val mediaRanges = MediaRange.parseAndSort(acceptHeader)

    mediaRanges
      .map(range => ContentType.fromString(range.contentType))
      .collectFirst(callback)
      .getOrElse(
        throw new NotAcceptableException(
          com.twitter.finagle.http.MediaType.PlainTextUtf8,
          Seq("Not Acceptable Media Type")
        )
      )
  }

  // Private

  private[http] def getScheme(request: Request): String = {
    request.headerMap.get("x-forwarded-proto") match {
      case Some(protocol) => protocol
      case _ => "http"
    }
  }

  private[http] def getAuthority(request: Request, uri: URI): String = {
    uri.getAuthority.toOption match {
      case Some(authority) => authority
      case _ => getHost(request)
    }
  }

  private[http] def getHost(request: Request): String = {
    request.host match {
      case Some(host) => host
      case _ => throw new BadRequestException("Host header not set")
    }
  }

  private[http] def getPath(request: Request, requestPath: String): String = {
    requestPath.toOption match {
      case Some(path) =>
        if (!path.startsWith("/")) {
          // relative
          val pathWithTrailingSlash = if (request.path.endsWith("/")) {
            request.path
          } else {
            request.path + "/"
          }
          pathWithTrailingSlash + path
        } else {
          path
        }
      case _ => ""
    }
  }

  private[http] def getQuery(uri: URI): String = {
    uri.getQuery.toOption match {
      case Some(query) => s"?$query"
      case _ => ""
    }
  }

  private[http] def getFragment(uri: URI): String = {
    uri.getFragment.toOption match {
      case Some(fragment) => s"#$fragment"
      case _ => ""
    }
  }
}
