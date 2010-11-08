/**************************************************************************
 *
 * Copyright (c) Adstream Holdings Pty Ltd
 *
 * This software is the confidential and proprietary information of
 * Adstream Holdings Pty Ltd ("Confidential Information").  You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Adstream Holdings Pty Ltd.
 *
 ************************************************************************
 */

package butter4s.date

import collection.SeqLike
import collection.mutable.{ListBuffer, Builder}
import java.util.{GregorianCalendar, Locale, Calendar}
import java.util.Calendar._

/**
 * @author Vladimir Kirichenko <vladimir.kirichenko@gmail.com> 
 */
case class Year( year: Int ) extends SeqLike[Month, Seq[Month]] {
	private[date] val calendar = new GregorianCalendar
	calendar.set( YEAR, year )
	private lazy val monthes = for ( m <- calendar.getActualMinimum( MONTH ) to calendar.getActualMaximum( MONTH ) ) yield Month( this, m )

	def iterator = monthes.toIterator

	def length = monthes.length

	def apply( idx: Int ) = monthes( idx )

	def newBuilder: Builder[Month, Seq[Month]] = new ListBuffer[Month]

	override def toString() = "Year(" + year + ")"
}

case class Month( year: Year, number: Int ) extends SeqLike[Day, Seq[Day]] {
	private[date] val calendar = year.calendar.clone.asInstanceOf[Calendar];
	calendar.set( MONTH, number )
	lazy val name = calendar.getDisplayName( MONTH, LONG, Locale.getDefault )
	private lazy val days = for ( d <- calendar.getActualMinimum( DATE ) to calendar.getActualMaximum( DATE ) ) yield Day( this, d )


	def iterator = days.toIterator

	def length = days.length

	def apply( idx: Int ) = days( idx )

	def newBuilder: Builder[Day, Seq[Day]] = new ListBuffer[Day]

	override def toString = name
}

case class Day( month: Month, number: Int ) {
	private[date] val calendar = month.calendar.clone.asInstanceOf[Calendar];
	calendar.set( DATE, number )
	lazy val ofWeek = calendar.get( DAY_OF_WEEK ) match {
		case MONDAY => Monday
		case TUESDAY => Tuesday
		case WEDNESDAY => Wednesday
		case THURSDAY => Thursday
		case FRIDAY => Friday
		case SATURDAY => Saturday
		case SUNDAY => Sunday
	}

	override def toString = ofWeek + "(" + month + "," + number + ")"
}

sealed trait DayOfWeek
case class Monday extends DayOfWeek
case class Tuesday extends DayOfWeek
case class Wednesday extends DayOfWeek
case class Thursday extends DayOfWeek
case class Friday extends DayOfWeek
case class Saturday extends DayOfWeek
case class Sunday extends DayOfWeek
