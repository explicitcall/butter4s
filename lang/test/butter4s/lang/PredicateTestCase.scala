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
package butter4s.lang

import org.junit.{Test, Assert}

/**
 * @author Vladimir Kirichenko <vladimir.kirichenko@gmail.com> 
 */
class PredicateTestCase {
	@Test
	def testAndOr = {
		val p1 = (s: String) => true
		val p2 = (s: AnyRef) => true

		Assert.assertTrue( ( p1 && p2 )( "" ) )
		Assert.assertFalse( ( p1 && not( p2 ) )( "" ) )

		Assert.assertTrue( ( not( p1 ) || p2 )( "" ) )
		Assert.assertFalse( ( not( p1 ) || not( p2 ) )( "" ) )

		println( not( p1 ) || p2 )
	}
}