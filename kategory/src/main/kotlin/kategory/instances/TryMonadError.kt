package kategory

interface TryMonadError : MonadError<Try.F, Throwable> {

    override fun <A, B> map(fa: TryKind<A>, f: (A) -> B): Try<B> = fa.ev().map(f)

    override fun <A> pure(a: A): Try<A> = Try.Success(a)

    override fun <A, B> flatMap(fa: TryKind<A>, f: (A) -> TryKind<B>): Try<B> = fa.ev().flatMap { f(it).ev() }

    override fun <A> raiseError(e: Throwable): Try<A> = Try.Failure(e)

    override fun <A> handleErrorWith(fa: TryKind<A>, f: (Throwable) -> TryKind<A>): Try<A> =
            fa.ev().recoverWith { f(it).ev() }

    @Suppress("UNCHECKED_CAST")
    override fun <A, B> tailRecM(a: A, f: (A) -> TryKind<Either<A, B>>): Try<B> =
            f(a).ev().fold({ Try.raiseError(it) }, { either -> either.fold({ tailRecM(it, f) }, { Try.Success(it) }) })
}