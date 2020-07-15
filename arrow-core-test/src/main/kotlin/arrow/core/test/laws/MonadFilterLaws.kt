package arrow.core.test.laws

import arrow.Kind
import arrow.core.extensions.eq
import arrow.core.test.generators.GenK
import arrow.core.test.generators.functionAToB
import arrow.typeclasses.Apply
import arrow.typeclasses.Eq
import arrow.typeclasses.EqK
import arrow.typeclasses.Functor
import arrow.typeclasses.MonadFilter
import arrow.typeclasses.Selective
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.forAll

object MonadFilterLaws {

  private fun <F> monadFilterLaws(
    MF: MonadFilter<F>,
    GENK: GenK<F>,
    EQK: EqK<F>
  ): List<Law> {
    val EQ = EQK.liftEq(Int.eq())
    val GEN = GENK.genK(Arb.int())
    val GEN_F = Arb.functionAToB<Int, Kind<F, Int>>(GEN)

    return listOf(
      Law("MonadFilter Laws: Left Empty") { MF.monadFilterLeftEmpty(GEN_F, EQ) },
      Law("MonadFilter Laws: Right Empty") { MF.monadFilterRightEmpty(GEN, EQ) },
      Law("MonadFilter Laws: Consistency") { MF.monadFilterConsistency(GEN, EQ) },
      Law("MonadFilter Laws: Comprehension Guards") { MF.monadFilterEmptyComprehensions(EQ) },
      Law("MonadFilter Laws: Comprehension bindWithFilter Guards") { MF.monadFilterBindWithFilterComprehensions(GEN, EQ) })
  }

  fun <F> laws(
    MF: MonadFilter<F>,
    GENK: GenK<F>,
    EQK: EqK<F>
  ): List<Law> =
    MonadLaws.laws(MF, GENK, EQK) +
      FunctorFilterLaws.laws(MF, GENK, EQK) +
      monadFilterLaws(MF, GENK, EQK)

  fun <F> laws(
    MF: MonadFilter<F>,
    FF: Functor<F>,
    AP: Apply<F>,
    SL: Selective<F>,
    GENK: GenK<F>,
    EQK: EqK<F>
  ): List<Law> =
    MonadLaws.laws(MF, FF, AP, SL, GENK, EQK) +
      FunctorFilterLaws.laws(MF, GENK, EQK) +
      monadFilterLaws(MF, GENK, EQK)

  private suspend fun <F, A> MonadFilter<F>.monadFilterLeftEmpty(G: Arb<Function1<A, Kind<F, A>>>, EQ: Eq<Kind<F, A>>): Unit =
    forAll(G) { f: (A) -> Kind<F, A> ->
      empty<A>().flatMap(f).equalUnderTheLaw(empty(), EQ)
    }

  private suspend fun <F, A> MonadFilter<F>.monadFilterRightEmpty(G: Arb<Kind<F, A>>, EQ: Eq<Kind<F, A>>): Unit =
    forAll(G) { fa: Kind<F, A> ->
      fa.flatMap { empty<A>() }.equalUnderTheLaw(empty(), EQ)
    }

  private suspend fun <F, A> MonadFilter<F>.monadFilterConsistency(G: Arb<Kind<F, A>>, EQ: Eq<Kind<F, A>>): Unit =
    forAll(Arb.functionAToB<A, Boolean>(Arb.bool()), G) { f: (A) -> Boolean, fa: Kind<F, A> ->
      fa.filter(f).equalUnderTheLaw(fa.flatMap { a -> if (f(a)) just(a) else empty() }, EQ)
    }

  fun <F> MonadFilter<F>.monadFilterEmptyComprehensions(EQ: Eq<Kind<F, Int>>): Unit =
    forAll(Arb.bool(), Arb.int()) { guard: Boolean, n: Int ->
      fx.monadFilter {
        continueIf(guard)
        n
      }.equalUnderTheLaw(if (!guard) empty() else just(n), EQ)
    }

  fun <F, A> MonadFilter<F>.monadFilterBindWithFilterComprehensions(G: Arb<Kind<F, A>>, EQ: Eq<Kind<F, A>>): Unit =
    forAll(Arb.bool(), G) { guard: Boolean, fa: Kind<F, A> ->
      fx.monadFilter {
        val x = fa.bindWithFilter { guard }
        x
      }.equalUnderTheLaw(if (!guard) empty() else fa, EQ)
    }
}
