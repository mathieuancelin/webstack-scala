package org.reactivecouchbase.webstack.result

case class HttpStatus(value: Int, reasonPhrase: String)

object HttpStatus {
  val CONTINUE = HttpStatus(100, "Continue")
  val SWITCHING_PROTOCOLS = HttpStatus(101, "Switching Protocols")
  val PROCESSING = HttpStatus(102, "Processing")
  val CHECKPOINT = HttpStatus(103, "Checkpoint")
  val OK = HttpStatus(200, "OK")
  val CREATED = HttpStatus(201, "Created")
  val ACCEPTED = HttpStatus(202, "Accepted")
  val NON_AUTHORITATIVE_INFORMATION = HttpStatus(203, "Non-Authoritative Information")
  val NO_CONTENT = HttpStatus(204, "No Content")
  val RESET_CONTENT = HttpStatus(205, "Reset Content")
  val PARTIAL_CONTENT = HttpStatus(206, "Partial Content")
  val MULTI_STATUS = HttpStatus(207, "Multi-Status")
  val ALREADY_REPORTED = HttpStatus(208, "Already Reported")
  val IM_USED = HttpStatus(226, "IM Used")
  val MULTIPLE_CHOICES = HttpStatus(300, "Multiple Choices")
  val MOVED_PERMANENTLY = HttpStatus(301, "Moved Permanently")
  val FOUND = HttpStatus(302, "Found")
  val MOVED_TEMPORARILY = HttpStatus(302, "Moved Temporarily")
  val SEE_OTHER = HttpStatus(303, "See Other")
  val NOT_MODIFIED = HttpStatus(304, "Not Modified")
  val USE_PROXY = HttpStatus(305, "Use Proxy")
  val TEMPORARY_REDIRECT = HttpStatus(307, "Temporary Redirect")
  val PERMANENT_REDIRECT = HttpStatus(308, "Permanent Redirect")
  val BAD_REQUEST = HttpStatus(400, "Bad Request")
  val UNAUTHORIZED = HttpStatus(401, "Unauthorized")
  val PAYMENT_REQUIRED = HttpStatus(402, "Payment Required")
  val FORBIDDEN = HttpStatus(403, "Forbidden")
  val NOT_FOUND = HttpStatus(404, "Not Found")
  val METHOD_NOT_ALLOWED = HttpStatus(405, "Method Not Allowed")
  val NOT_ACCEPTABLE = HttpStatus(406, "Not Acceptable")
  val PROXY_AUTHENTICATION_REQUIRED = HttpStatus(407, "Proxy Authentication Required")
  val REQUEST_TIMEOUT = HttpStatus(408, "Request Timeout")
  val CONFLICT = HttpStatus(409, "Conflict")
  val GONE = HttpStatus(410, "Gone")
  val LENGTH_REQUIRED = HttpStatus(411, "Length Required")
  val PRECONDITION_FAILED = HttpStatus(412, "Precondition Failed")
  val PAYLOAD_TOO_LARGE = HttpStatus(413, "Payload Too Large")
  val REQUEST_ENTITY_TOO_LARGE = HttpStatus(413, "Request Entity Too Large")
  val URI_TOO_LONG = HttpStatus(414, "URI Too Long")
  val REQUEST_URI_TOO_LONG = HttpStatus(414, "Request-URI Too Long")
  val UNSUPPORTED_MEDIA_TYPE = HttpStatus(415, "Unsupported Media Type")
  val REQUESTED_RANGE_NOT_SATISFIABLE = HttpStatus(416, "Requested range not satisfiable")
  val EXPECTATION_FAILED = HttpStatus(417, "Expectation Failed")
  val I_AM_A_TEAPOT = HttpStatus(418, "I'm a teapot")
  val INSUFFICIENT_SPACE_ON_RESOURCE = HttpStatus(419, "Insufficient Space On Resource")
  val METHOD_FAILURE = HttpStatus(420, "Method Failure")
  val DESTINATION_LOCKED = HttpStatus(421, "Destination Locked")
  val UNPROCESSABLE_ENTITY = HttpStatus(422, "Unprocessable Entity")
  val LOCKED = HttpStatus(423, "Locked")
  val FAILED_DEPENDENCY = HttpStatus(424, "Failed Dependency")
  val UPGRADE_REQUIRED = HttpStatus(426, "Upgrade Required")
  val PRECONDITION_REQUIRED = HttpStatus(428, "Precondition Required")
  val TOO_MANY_REQUESTS = HttpStatus(429, "Too Many Requests")
  val REQUEST_HEADER_FIELDS_TOO_LARGE = HttpStatus(431, "Request Header Fields Too Large")
  val UNAVAILABLE_FOR_LEGAL_REASONS = HttpStatus(451, "Unavailable For Legal Reasons")
  val INTERNAL_SERVER_ERROR = HttpStatus(500, "Internal Server Error")
  val NOT_IMPLEMENTED = HttpStatus(501, "Not Implemented")
  val BAD_GATEWAY = HttpStatus(502, "Bad Gateway")
  val SERVICE_UNAVAILABLE = HttpStatus(503, "Service Unavailable")
  val GATEWAY_TIMEOUT = HttpStatus(504, "Gateway Timeout")
  val HTTP_VERSION_NOT_SUPPORTED = HttpStatus(505, "HTTP Version not supported")
  val VARIANT_ALSO_NEGOTIATES = HttpStatus(506, "Variant Also Negotiates")
  val INSUFFICIENT_STORAGE = HttpStatus(507, "Insufficient Storage")
  val LOOP_DETECTED = HttpStatus(508, "Loop Detected")
  val BANDWIDTH_LIMIT_EXCEEDED = HttpStatus(509, "Bandwidth Limit Exceeded")
  val NOT_EXTENDED = HttpStatus(510, "Not Extended")
  val NETWORK_AUTHENTICATION_REQUIRED = HttpStatus(511, "Network Authentication Required")
}

object MediaType {
  val ALL_VALUE = "*/*"
  val APPLICATION_ATOM_XML_VALUE = "application/atom+xml"
  val APPLICATION_FORM_URLENCODED_VALUE = "application/x-www-form-urlencoded"
  val APPLICATION_JSON_VALUE = "application/json"
  val APPLICATION_JSON_UTF8_VALUE = APPLICATION_JSON_VALUE + ";charset=UTF-8"
  val APPLICATION_OCTET_STREAM_VALUE = "application/octet-stream"
  val APPLICATION_PDF_VALUE = "application/pdf"
  val APPLICATION_XHTML_XML_VALUE = "application/xhtml+xml"
  val APPLICATION_XML_VALUE = "application/xml"
  val IMAGE_GIF_VALUE = "image/gif"
  val IMAGE_JPEG_VALUE = "image/jpeg"
  val IMAGE_PNG_VALUE = "image/png"
  val MULTIPART_FORM_DATA_VALUE = "multipart/form-data"
  val TEXT_EVENT_STREAM_VALUE = "text/event-stream"
  val TEXT_HTML_VALUE = "text/html"
  val TEXT_MARKDOWN_VALUE = "text/markdown"
  val TEXT_PLAIN_VALUE = "text/plain"
  val TEXT_XML_VALUE = "text/xml"
}


