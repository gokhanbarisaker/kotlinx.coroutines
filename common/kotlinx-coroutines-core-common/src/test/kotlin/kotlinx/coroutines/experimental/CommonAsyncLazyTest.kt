/*
 * Copyright 2016-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:Suppress("NAMED_ARGUMENTS_NOT_ALLOWED") // KT-21913

package kotlinx.coroutines.experimental

import kotlin.coroutines.experimental.*
import kotlin.test.*

class CommonAsyncLazyTest : TestBase() {
    @Test
    fun testSimple() = runTest {
        expect(1)
        val d = async(coroutineContext, CoroutineStart.LAZY) {
            expect(3)
            42
        }
        expect(2)
        assertTrue(!d.isActive && !d.isCompleted)
        assertTrue(d.await() == 42)
        assertTrue(!d.isActive && d.isCompleted && !d.isCompletedExceptionally)
        expect(4)
        assertTrue(d.await() == 42) // second await -- same result
        finish(5)
    }

    @Test
    fun testLazyDeferAndYield() = runTest {
        expect(1)
        val d = async(coroutineContext, CoroutineStart.LAZY) {
            expect(3)
            yield() // this has not effect, because parent coroutine is waiting
            expect(4)
            42
        }
        expect(2)
        assertTrue(!d.isActive && !d.isCompleted)
        assertTrue(d.await() == 42)
        assertTrue(!d.isActive && d.isCompleted && !d.isCompletedExceptionally)
        expect(5)
        assertTrue(d.await() == 42) // second await -- same result
        finish(6)
    }

    @Test
    fun testLazyDeferAndYield2() = runTest {
        expect(1)
        val d = async(coroutineContext, CoroutineStart.LAZY) {
            expect(7)
            42
        }
        expect(2)
        assertTrue(!d.isActive && !d.isCompleted)
        launch(coroutineContext) { // see how it looks from another coroutine
            expect(4)
            assertTrue(!d.isActive && !d.isCompleted)
            yield() // yield back to main
            expect(6)
            assertTrue(d.isActive && !d.isCompleted) // implicitly started by main's await
            yield() // yield to d
        }
        expect(3)
        assertTrue(!d.isActive && !d.isCompleted)
        yield() // yield to second child (lazy async is not computing yet)
        expect(5)
        assertTrue(!d.isActive && !d.isCompleted)
        assertTrue(d.await() == 42) // starts computing
        assertTrue(!d.isActive && d.isCompleted && !d.isCompletedExceptionally)
        finish(8)
    }

    @Test
    fun testSimpleException() = runTest(
        expected = { it is TestException }
    ) {
        expect(1)
        val d = async(coroutineContext, CoroutineStart.LAZY) {
            finish(3)
            throw TestException()
        }
        expect(2)
        assertTrue(!d.isActive && !d.isCompleted)
        d.await() // will throw IOException
    }

    @Test
    fun testLazyDeferAndYieldException() =  runTest(
        expected = { it is TestException }
    ) {
        expect(1)
        val d = async(coroutineContext, CoroutineStart.LAZY) {
            expect(3)
            yield() // this has not effect, because parent coroutine is waiting
            finish(4)
            throw TestException()
        }
        expect(2)
        assertTrue(!d.isActive && !d.isCompleted)
        d.await() // will throw IOException
    }

    @Test
    fun testCatchException() = runTest {
        expect(1)
        val d = async(coroutineContext, CoroutineStart.LAZY) {
            expect(3)
            throw TestException()
        }
        expect(2)
        assertTrue(!d.isActive && !d.isCompleted)
        try {
            d.await() // will throw IOException
        } catch (e: TestException) {
            assertTrue(!d.isActive && d.isCompleted && d.isCompletedExceptionally && !d.isCancelled)
            expect(4)
        }
        finish(5)
    }

    @Test
    fun testStart() = runTest {
        expect(1)
        val d = async(coroutineContext, CoroutineStart.LAZY) {
            expect(4)
            42
        }
        expect(2)
        assertTrue(!d.isActive && !d.isCompleted)
        assertTrue(d.start())
        assertTrue(d.isActive && !d.isCompleted)
        expect(3)
        assertTrue(!d.start())
        yield() // yield to started coroutine
        assertTrue(!d.isActive && d.isCompleted && !d.isCompletedExceptionally) // and it finishes
        expect(5)
        assertTrue(d.await() == 42) // await sees result
        finish(6)
    }

    @Test
    fun testCancelBeforeStart() = runTest(
        expected = { it is JobCancellationException }
    ) {
        expect(1)
        val d = async(coroutineContext, CoroutineStart.LAZY) {
            expectUnreached()
            42
        }
        expect(2)
        assertTrue(!d.isActive && !d.isCompleted)
        assertTrue(d.cancel())
        assertTrue(!d.isActive && d.isCompleted && d.isCompletedExceptionally && d.isCancelled)
        assertTrue(!d.cancel())
        assertTrue(!d.start())
        finish(3)
        assertTrue(d.await() == 42) // await shall throw CancellationException
        expectUnreached()
    }

    @Test
    fun testCancelWhileComputing() = runTest(
        expected = { it is CancellationException }
    ) {
        expect(1)
        val d = async(coroutineContext, CoroutineStart.LAZY) {
            expect(4)
            yield() // yield to main, that is going to cancel us
            expectUnreached()
            42
        }
        expect(2)
        assertTrue(!d.isActive && !d.isCompleted && !d.isCancelled)
        assertTrue(d.start())
        assertTrue(d.isActive && !d.isCompleted && !d.isCancelled)
        expect(3)
        yield() // yield to d
        expect(5)
        assertTrue(d.isActive && !d.isCompleted && !d.isCancelled)
        assertTrue(d.cancel())
        assertTrue(!d.isActive && !d.isCompletedExceptionally && d.isCancelled) // cancelling !
        assertTrue(!d.cancel())
        assertTrue(!d.isActive && !d.isCompletedExceptionally && d.isCancelled) // still cancelling
        finish(6)
        assertTrue(d.await() == 42) // await shall throw CancellationException
        expectUnreached()
    }

    private class TestException : Exception()
}