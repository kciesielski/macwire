package com.softwaremill.macwire

import reflect.macros.blackbox.Context
import annotation.tailrec

private[macwire] class ValuesOfTypeInEnclosingClassFinder[C <: Context](val c: C, debug: Debug) {
  import c.universe._

  private val typeCheckUtil = new TypeCheckUtil[c.type](c, debug)

  def find(t: Type): List[Name] = {
    @tailrec
    def doFind(trees: List[Tree], acc: List[Name]): List[Name] = trees match {
      case Nil => acc
      case tree :: tail => tree match {
        case ValDef(_, name, tpt, rhs) =>
          val candidateOk = typeCheckUtil.checkCandidate(t, name, tpt, treeToCheck(tree, rhs), "val")
          doFind(tail, if (candidateOk) name :: acc else acc)
        case DefDef(_, name, _, _, tpt, rhs) =>
          val candidateOk = typeCheckUtil.checkCandidate(t, name, tpt, treeToCheck(tree, rhs), "def")
          doFind(tail, if (candidateOk) name :: acc else acc)
        case _ => doFind(tail, acc)
      }
    }

    def treeToCheck(tree: Tree, rhs: Tree) = {
      // If possible, we check the definition (rhs). We can't always check the tree, as it would cause recursive
      // type ascription needed errors from the compiler.
      if (rhs.isEmpty) tree else rhs
    }

    val enclosingClassBody = c.enclosingClass match {
      case ClassDef(_, _, _, Template(_, _, body)) => body
      case ModuleDef(_, _, Template(_, _, body)) => body
      case e =>
        c.error(c.enclosingPosition, s"Unknown type of enclosing class: ${e.getClass}")
        Nil
    }

    debug("Looking in the enclosing class/trait")
    doFind(enclosingClassBody, Nil)
  }
}
