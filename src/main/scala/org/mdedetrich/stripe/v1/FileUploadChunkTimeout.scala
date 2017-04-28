package org.mdedetrich.stripe.v1

import scala.concurrent.duration.FiniteDuration

/**
  * Timeout used when chunking file uploads
  * @param duration
  */
case class FileUploadChunkTimeout(duration: FiniteDuration) extends AnyVal
