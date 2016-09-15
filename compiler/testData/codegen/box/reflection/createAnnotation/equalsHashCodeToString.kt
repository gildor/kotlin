// WITH_REFLECT

package test

annotation class A
annotation class B(val s: String)

fun box(): String {
    val createA = A::class.constructors.single()

    val a1 = createA.call()
    if (a1.toString() != "@test.A()") return "Fail: toString does not correspond to the documentation of java.lang.annotation.Annotation#toString: $a1"

    val a2 = createA.call()
    if (a1 === a2) return "Fail: instances created by the constructor should be different"
    if (a1 != a2) return "Fail: any instance of A should be equal to any other instance of A"
    if (a1.hashCode() != a2.hashCode()) return "Fail: hash codes of equal instances should be equal"
    if (a1.hashCode() != 0) return "Fail: hashCode does not correspond to the documentation of java.lang.annotation.Annotation#hashCode: ${a1.hashCode()}"

    // val createB = B::class.constructors.single()

    return "OK"
}
