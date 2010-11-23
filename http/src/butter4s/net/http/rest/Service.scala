/*
 * The MIT License
 *
 * Copyright (c) 2010 Vladimir Kirichenko <vladimir.kirichenko@gmail.com>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package butter4s.net.http.rest

import butter4s.reflect._
import butter4s.lang._
import butter4s.io._
import butter4s.bind.json.JsonBind
import butter4s.logging.Logging
import java.lang.reflect.{ParameterizedType, InvocationTargetException}
import java.io.{InputStream, Writer}
import butter4s.net.http.rest.Method.Constants

/**
 * @author Vladimir Kirichenko <vladimir.kirichenko@gmail.com> 
 */
trait Session {
	def apply[A]( name: String ): Option[A]

	def update( name: String, value: Any ): Unit

	def invalidate: Unit
}

trait Context {
	val serviceName: String

	val serviceLocation: String
}

object Request {
	private def compile( mapping: String ) = mapping.replaceAll( "\\{([^\\}]*)\\}", "([^/]*)" ).r

	def pathParam( mapping: String, requestLine: String, name: String ) =
		"\\{([^\\}]*)\\}".r.findAllIn( mapping ).indexOf( "{" + name + "}" ) match {
			case -1 => None
			case group => compile( mapping ).findFirstMatchIn( requestLine ).map( _.group( group + 1 ) )
		}

	def methodMatches( requestLine: String, m: butter4s.reflect.Method ) = m.annotation[Method] match {
		case None => false
		case Some( restMethod ) =>
			if ( restMethod.path == Method.Constants.DEFAULT ) requestLine == "/" + m.name
			else compile( restMethod.path ).findFirstMatchIn( requestLine ).isDefined
	}
}

trait Request {
	val requestLine: String

	val context: Context

	def parameter( mapping: String, name: String ) = Request.pathParam( mapping, requestLine, name )

	def parameter( name: String ): Option[String]

	def parameters( name: String ): List[String]

	val body: InputStream

	val session: Session

	override def toString = requestLine
}

object Response {
	object Code {
		final val CONTINUE = 100;
		final val SWITCHING_PROTOCOLS = 101;
		final val OK = 200;
		final val CREATED = 201;
		final val ACCEPTED = 202;
		final val NON_AUTHORITATIVE_INFORMATION = 203;
		final val NO_CONTENT = 204;
		final val RESET_CONTENT = 205;
		final val PARTIAL_CONTENT = 206;
		final val MULTIPLE_CHOICES = 300;
		final val MOVED_PERMANENTLY = 301;
		final val MOVED_TEMPORARILY = 302;
		final val FOUND = 302;
		final val SEE_OTHER = 303;
		final val NOT_MODIFIED = 304;
		final val USE_PROXY = 305;
		final val TEMPORARY_REDIRECT = 307;
		final val BAD_REQUEST = 400;
		final val UNAUTHORIZED = 401;
		final val PAYMENT_REQUIRED = 402;
		final val FORBIDDEN = 403;
		final val NOT_FOUND = 404;
		final val METHOD_NOT_ALLOWED = 405;
		final val NOT_ACCEPTABLE = 406;
		final val PROXY_AUTHENTICATION_REQUIRED = 407;
		final val REQUEST_TIMEOUT = 408;
		final val CONFLICT = 409;
		final val GONE = 410;
		final val LENGTH_REQUIRED = 411;
		final val PRECONDITION_FAILED = 412;
		final val REQUEST_ENTITY_TOO_LARGE = 413;
		final val REQUEST_URI_TOO_LONG = 414;
		final val UNSUPPORTED_MEDIA_TYPE = 415;
		final val REQUESTED_RANGE_NOT_SATISFIABLE = 416;
		final val EXPECTATION_FAILED = 417;
		final val INTERNAL_SERVER_ERROR = 500;
		final val NOT_IMPLEMENTED = 501;
		final val BAD_GATEWAY = 502;
		final val SERVICE_UNAVAILABLE = 503;
		final val GATEWAY_TIMEOUT = 504;
		final val HTTP_VERSION_NOT_SUPPORTED = 505;
	}
}

trait Response {
	def content( contentType: String, what: ( => Writer ) => Unit ): Unit

	def status( code: Int, message: String = null ): Unit
}

class ImmediateResponse( val code: Int, val message: String ) extends RuntimeException( message )

trait ContentProducer {
	def marshal( content: Any ): String
}

trait ParameterConvertor {
	def convert( value: String, t: Type )
}

object Service {
	private[rest] var producers = Map[String, Any => String](
		MimeType.TEXT_JAVASCRIPT -> ( content => String.valueOf( content ) ),
		MimeType.APPLICATION_JSON -> ( content => JsonBind.marshal( content ) )
		)

	def registerContentProducer( contentType: String, cp: ContentProducer ): Unit = registerContentProducer( contentType, cp.marshal( _ ) )

	def registerContentProducer( contentType: String, produce: Any => String ) = producers += contentType -> produce

	private var converters = Map[String, (String, java.lang.reflect.Type) => Any](
		classOf[Int].getName -> {(s, _) => s.toInt},
		classOf[java.lang.Integer].getName -> {(s, _) => s.toInt},
		classOf[Long].getName -> {(s, _) => s.toLong},
		classOf[java.lang.Long].getName -> {(s, _) => s.toLong},
		classOf[Short].getName -> ( (s, _) => s.toShort ),
		classOf[java.lang.Short].getName -> ( (s, _) => s.toShort ),
		classOf[Byte].getName -> ( (s, _) => s.toByte ),
		classOf[java.lang.Byte].getName -> ( (s, _) => s.toByte ),
		classOf[Float].getName -> ( (s, _) => s.toFloat ),
		classOf[java.lang.Float].getName -> ( (s, _) => s.toFloat ),
		classOf[Double].getName -> ( (s, _) => s.toDouble ),
		classOf[java.lang.Double].getName -> {(s, _) => s.toDouble},
		classOf[Boolean].getName -> {(s, _) => s.toBoolean},
		classOf[java.lang.Boolean].getName -> {(s, _) => s.toBoolean},
		classOf[String].getName -> {(s, _) => s},
		MimeType.APPLICATION_JSON -> {(s, t) => JsonBind.unmarshal( s, t ).get}
		)

	def convert( value: String, hint: String, targetType: Type ) =
		( if ( hint == MimeType.APPLICATION_JAVA_CLASS ) converters( targetType.toClass[AnyRef].getName )
		else converters( hint ) )( value, targetType )

	def registerParameterBinder( typeHint: String, pc: ParameterConvertor ): Unit = registerParameterBinder( typeHint, pc.convert( _, _ ) )

	def registerParameterBinder( typeHint: String, convert: (String, java.lang.reflect.Type) => Any ) = converters += typeHint -> convert

}

trait Service extends Logging {
	import Response.Code._

	def perform( request: Request, response: Response ) = log.time( request.toString, try {
		log.debug( "invoke " + request )
		getClass.declaredMethod( Request.methodMatches( request.requestLine, _ ) ) match {
			case None => respond( NOT_FOUND, request.requestLine + " is unbound" )
			case Some( method ) => method.annotation[Method] match {
				case None => respond( NOT_FOUND, method.name + " is not exposed" )
				case Some( restMethod ) =>
					val result = method.invoke( this, method.parameters.map( p => {
						log.debug( p )
						if ( p.genericType.assignableFrom[Request] ) request
						else if ( p.genericType.assignableFrom[List[_]] ) p.annotation[Param] match {
							case None => respond( INTERNAL_SERVER_ERROR, "method parameter of type " + p.genericType + " is not annotated properly" )
							case Some( restParam ) => request.parameters( restParam.name ).map( Service.convert( _, restParam.typeHint, p.genericType.asInstanceOf[ParameterizedType].getActualTypeArguments()( 0 ) ) )
						} else p.annotation[Param] match {
							case None => respond( INTERNAL_SERVER_ERROR, "method parameter of type " + p.genericType + " is not annotated properly" )
							case Some( restParam ) => Service.convert( ( restParam.from match {
								case Param.From.BODY => Some( request.body.readAs[String] )
								case Param.From.QUERY => request.parameter( restParam.name )
								case Param.From.PATH => request.parameter( restMethod.path, restParam.name )
							} ) match {
								case None => respond( BAD_REQUEST, restParam.name + " is required" )
								case Some( value ) => value
							}, restParam.typeHint, p.genericType ).asInstanceOf[AnyRef]
						}
					} ): _ * )

					if ( restMethod.raw ) response.content( restMethod.produces + "; charset=" + restMethod.charset, _.write( String.valueOf( result ) ) )
					else Service.producers.get( restMethod.produces ) match {
						case None => response.status( if ( result == null ) OK else result.asInstanceOf[Int] )
						case Some( toContent ) => response.content( restMethod.produces + "; charset=" + restMethod.charset, _.write( toContent( result ) ) )
					}
			}
		}
	} catch {
		case e: ImmediateResponse => log.error( e, e );
		response.status( e.code, e.message )
		case e: InvocationTargetException if e.getTargetException.isInstanceOf[ImmediateResponse] => log.error( e.getTargetException, e.getTargetException );
		response.status( e.getTargetException.asInstanceOf[ImmediateResponse].code, e.getTargetException.getMessage )
		case e: InvocationTargetException => throw e.getTargetException
	} )

	@Method( produces = "text/javascript" )
	def api( request: Request ) = "var api_" + request.context.serviceName + " = { \n\tsync: {\n" + getClass.declaredMethods.view.filter( m => m.annotatedWith[Method] && m.name != "api" ).map(
		method => {
			val params = method.parameters.view.filter( _.annotatedWith[Param] )
			val (queryParams, pathParams) = params.partition( _.annotation[Param].get.from == Param.From.QUERY )
			val restMethod = method.annotation[Method].get
			"\t\t" + method.name + ": function (" + params.map( _.annotation[Param].get.name ).mkString( "," ) + ") {\n" +
					"\t\t\tvar result, error;\n" +
					"\t\t\tnew Ajax.Request( '" + request.context.serviceLocation +
					( if ( restMethod.path == Method.Constants.DEFAULT ) "/" + method.name else restMethod.path.replaceAll( "\\{", "'+" ).replaceAll( "\\}", "+'" ) ) + "', {\n" +
					"\t\t\t\tparameters: {\n" +
					queryParams.map( p => {
						val restParam = p.annotation[Param].get
						"\t\t\t\t\t" + restParam.name + ":" + ( if ( p.genericType.assignableFrom[List[_]] ) restParam.name + ".collect(function(x){return " +
								wrapIf( restParam.typeHint != MimeType.APPLICATION_JAVA_CLASS )( "Object.toJSON(", "x", ")" ) + ";})" else
							wrapIf( restParam.typeHint != MimeType.APPLICATION_JAVA_CLASS )( "Object.toJSON(", restParam.name, ")" ) )
					} ).mkString( ",\n" ) + "\n" +
					"\t\t\t\t},\n" +
					"\t\t\t\tmethod: '" + restMethod.http + "',\n" +
					"\t\t\t\tevalJSON: " + MimeType.isJson( restMethod.produces ) + ",\n" +
					"\t\t\t\tevalJS: false,\n" +
					"\t\t\t\tasynchronous: false,\n" +
					"\t\t\t\tonSuccess: function( response ) {\n" +
					( if ( MimeType.isJson( restMethod.produces ) ) "\t\t\t\t\tresult = response.responseJSON;\n"
					else if ( restMethod.produces == Constants.NONE ) "\t\t\t\t\tresult = response.status;\n" else "\t\t\t\t\tresult = response.responseText;\n" ) +
					"\t\t\t\t},\n" +
					"\t\t\t\tonFailure: function( response ) { \n" +
					"\t\t\t\t\terror = response.statusText;\n" +
					"\t\t\t\t}\n" +
					"\t\t\t});\n" +
					"\t\t\tif (error) throw error; else return result;\n" +
					"\t\t}"
		} ).mkString( ",\n\n" ) + "\n\t},\n\tasync: {\n" + getClass.declaredMethods.view.filter( m => m.annotatedWith[Method] && m.name != "api" ).map(
		method => {
			val params = method.parameters.view.filter( _.annotatedWith[Param] )
			val (queryParams, pathParams) = params.partition( _.annotation[Param].get.from == Param.From.QUERY )
			val restMethod = method.annotation[Method].get
			"\t\t" + method.name + ": function (" + ( params.map( _.annotation[Param].get.name ) :+ "succeed" :+ "failed" ).mkString( "," ) + ") {\n" +
					"\t\t\tnew Ajax.Request( '" + request.context.serviceLocation + 
					( if ( restMethod.path == Method.Constants.DEFAULT ) "/" + method.name else restMethod.path.replaceAll( "\\{", "'+" ).replaceAll( "\\}", "+'" ) ) + "', {\n" +
					"\t\t\t\tparameters: {\n" +
					queryParams.map( p => {
						val restParam = p.annotation[Param].get
						"\t\t\t\t\t" + restParam.name + ":" + ( if ( p.genericType.assignableFrom[List[_]] ) restParam.name + ".collect(function(x){return " +
								wrapIf( restParam.typeHint != MimeType.APPLICATION_JAVA_CLASS )( "Object.toJSON(", "x", ")" ) + ";})" else
							wrapIf( restParam.typeHint != MimeType.APPLICATION_JAVA_CLASS )( "Object.toJSON(", restParam.name, ")" ) )
					} ).mkString( ",\n" ) + "\n" +
					"\t\t\t\t},\n" +
					"\t\t\t\tmethod: '" + restMethod.http + "',\n" +
					"\t\t\t\tevalJSON: " + MimeType.isJson( restMethod.produces ) + ",\n" +
					"\t\t\t\tevalJS: false,\n" +
					"\t\t\t\tonSuccess: function( response ) {\n" +
					"\t\t\t\t\tif (succeed) succeed(" +
					( if ( MimeType.isJson( restMethod.produces ) ) "response.responseJSON"
					else if ( restMethod.produces == Constants.NONE ) "response.status" else "response.responseText" ) + ");\n" +
					"\t\t\t\t},\n" +
					"\t\t\t\tonFailure: function( response ) { \n" +
					"\t\t\t\t\tif (failed) failed(response.statusText); else alert(response.statusText);\n" +
					"\t\t\t\t}\n" +
					"\t\t\t});\n" +
					"\t\t}"
		} ).mkString( ",\n\n" ) + "\n\t}\n}"


	def respond( code: Int, reason: String ) = throw new ImmediateResponse( code, reason )

}


