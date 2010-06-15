package butter4s.servlet.rest

import butter4s.servlet._
import butter4s.reflect._
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import java.io.PrintWriter

/**
 * @author Vladimir Kirichenko <vladimir.kirichenko@gmail.com>
 */
class RestServlet extends Servlet {
	override def post( request: Request, response: Response ) = get( request, response )

	override def get( request: Request, response: Response ) = {
		val action = request.getRequestURI.substring( request.getServletPath.length + 1 )
		val methodName = if ( action.contains( "/" ) ) action.substring( 0, action.indexOf( "/" ) ) else action
		getClass.declaredMethod( methodName ) match {
			case Some( method ) => method.annotation[RestAction] match {
				case Some( a ) => try {
					method.invoke( this, Array[AnyRef](
						new RestRequest( request, if ( a.path != RestConstants.DEFAULT ) a.path else method.name ),
						new RestResponse( response, a.produces + "; charset=" + a.charset )
						): _* )
				} catch {
					case e: RestError => response.sendError( e.code, e.message )
					case e => throw e
				}
				case None => response.sendError( HttpServletResponse.SC_NOT_FOUND, methodName + " is not a @RestAction" )
			}
			case None => response.sendError( HttpServletResponse.SC_NOT_FOUND, methodName + " not found" )
		}
	}
}

class RestRequest( impl: HttpServletRequest, path: String ) extends Request( impl ) {
	lazy val query = impl.getRequestURI.substring( impl.getServletPath.length + 1 + path.length + ( if ( path.endsWith( "/" ) ) 0 else 1 ) )
}

class RestResponse( impl: HttpServletResponse, contentType: String ) extends Response( impl ) {
	override def send( what: ( => PrintWriter ) => Unit ) = {
		impl.setContentType( contentType )
		super.send( what )
	}
}

class RestError( val code: Int, val message: String ) extends RuntimeException( code + " " + message )