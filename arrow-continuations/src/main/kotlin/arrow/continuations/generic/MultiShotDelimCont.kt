package arrow.continuations.generic

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.loop
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

open class MultiShotDelimContScope<R>(val f: suspend DelimitedScope<R>.() -> R) : RunnableDelimitedScope<R> {

  private val resultVar = atomic<R?>(null)
  private val nextShift = atomic<(suspend () -> R)?>(null)
  // TODO This can be append only and needs fast reversed access
  private val shiftFnContinuations = mutableListOf<Continuation<R>>()
  // TODO This can be append only and needs fast random access and slicing
  internal open val stack = mutableListOf<Any?>()

  class MultiShotCont<A, R>(
    liveContinuation: Continuation<A>,
    private val f: suspend DelimitedScope<R>.() -> R,
    private val stack: MutableList<Any?>,
    private val shiftFnContinuations: MutableList<Continuation<R>>
  ) : DelimitedContinuation<A, R> {
    private val liveContinuation = atomic<Continuation<A>?>(liveContinuation)
    private val stackOffset = stack.size

    override suspend fun invoke(a: A): R =
      when (val cont = liveContinuation.getAndSet(null)) {
        null -> PrefilledDelimContScope((stack.subList(0, stackOffset).toList() + a).toMutableList(), f).invoke()
        else -> suspendCoroutine { resumeShift ->
          shiftFnContinuations.add(resumeShift)
          stack.add(a)
          cont.resume(a)
        }
      }
  }

  override suspend fun <A> shift(func: suspend (DelimitedContinuation<A, R>) -> R): A = suspendCoroutine { continueMain ->
    val c = MultiShotCont(continueMain, f, stack, shiftFnContinuations)
    assert(nextShift.compareAndSet(null, suspend { func(c) }))
  }

  override fun invoke(): R {
    f.startCoroutineUninterceptedOrReturn(this, Continuation(EmptyCoroutineContext) { result ->
      resultVar.value = result.getOrThrow()
    }).let {
      if (it == COROUTINE_SUSPENDED) {
        resultVar.loop { mRes ->
          if (mRes == null) {
            val nextShiftFn = nextShift.getAndSet(null)
              ?: throw IllegalStateException("No further work to do but also no result!")
            nextShiftFn.startCoroutineUninterceptedOrReturn(Continuation(EmptyCoroutineContext) { result ->
              resultVar.value = result.getOrThrow()
            }).let {
              if (it != COROUTINE_SUSPENDED) resultVar.value = it as R
            }
          } else return@let
        }
      }
      else return@invoke it as R
    }
    assert(resultVar.value != null)
    for (c in shiftFnContinuations.asReversed()) c.resume(resultVar.value!!)
    return resultVar.value!!
  }

  companion object {
    fun <R> reset(f: suspend DelimitedScope<R>.() -> R): R = MultiShotDelimContScope(f).invoke()
  }
}

class PrefilledDelimContScope<R>(
  override val stack: MutableList<Any?>,
  f: suspend DelimitedScope<R>.() -> R
): MultiShotDelimContScope<R>(f) {
  var depth = 0

  override suspend fun <A> shift(func: suspend (DelimitedContinuation<A, R>) -> R): A =
    if (stack.size > depth) stack[depth++] as A
    else super.shift(func).also { depth++ }
}