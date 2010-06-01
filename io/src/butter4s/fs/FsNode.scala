package butter4s.fs

import java.io.FileOutputStream
import butter4s.io._

/**
 * @author Vladimir Kirichenko <vladimir.kirichenko@gmail.com>
 */

abstract class FsNode( _path: String ) {
	val impl = new java.io.File( _path )

	def exists = impl.exists

	lazy val name = impl.getName

	lazy val path = impl.getPath

	override def toString = impl.toString
}


class File( path: String ) extends FsNode( path ) {
	def read[R]( implicit toResult: Array[Byte] => R ) = using( new FileInputStream( path ) ) {readAs[R]( _ )}

	def write[P]( content: P )( implicit fromParam: P => Array[Byte] ) = {
		parent.create
		using( new FileOutputStream( impl ) ) {copy( content, _ )}
	}

	def length = impl.length

	lazy val parent = new Directory( impl.getParent )
}

class Directory( path: String ) extends FsNode( path ) {
	def list = impl.listFiles.map( file =>
		if ( file.isDirectory ) new Directory( file.getPath )
		else new File( file.getPath )
		)

	def create = impl.mkdirs

	def list( p: FsNode => Boolean ): Array[FsNode] = list.filter( p )
}