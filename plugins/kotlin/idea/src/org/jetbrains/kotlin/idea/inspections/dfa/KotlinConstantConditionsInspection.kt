package org.jetbrains.kotlin.idea.inspections.dfa

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.dataFlow.interpreter.StandardDataFlowInterpreter
import com.intellij.codeInspection.dataFlow.jvm.JvmDfaMemoryStateImpl
import com.intellij.codeInspection.dataFlow.lang.DfaAnchor
import com.intellij.codeInspection.dataFlow.lang.DfaListener
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState
import com.intellij.codeInspection.dataFlow.types.DfTypes
import com.intellij.codeInspection.dataFlow.value.DfaValue
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.namedFunctionVisitor

class KotlinConstantConditionsInspection : AbstractKotlinInspection() {
    private enum class ConstantValue {
        TRUE, FALSE, UNKNOWN
    }
    
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = namedFunctionVisitor(fun(function) {
        val body = function.bodyExpression ?: function.bodyBlockExpression ?: return
        val factory = DfaValueFactory(holder.project)
        val flow = KtControlFlowBuilder(factory, body).buildFlow() ?: return
        val state = JvmDfaMemoryStateImpl(factory)
        val constantConditions : MutableMap<KtExpression, ConstantValue> = hashMapOf()
        val listener = object : DfaListener {
            override fun beforePush(args: Array<out DfaValue>, value: DfaValue, anchor: DfaAnchor, state: DfaMemoryState) {
                if (anchor is KotlinExpressionAnchor) {
                    val expression = anchor.expression
                    val oldVal = constantConditions[expression]
                    if (oldVal == ConstantValue.UNKNOWN) return
                    var newVal = when(state.getDfType(value)) {
                        DfTypes.TRUE -> ConstantValue.TRUE
                        DfTypes.FALSE -> ConstantValue.FALSE
                        else -> ConstantValue.UNKNOWN
                    }
                    if (oldVal != null && oldVal != newVal) {
                        newVal = ConstantValue.UNKNOWN
                    }
                    constantConditions[expression] = newVal
                }
            }
        }
        val interpreter = StandardDataFlowInterpreter(flow, listener)
        interpreter.interpret(state)
        constantConditions.forEach { (expr, cv) -> 
            if (cv != ConstantValue.UNKNOWN) {
                val key = if (cv == ConstantValue.TRUE) "inspection.message.condition.always.true" 
                else "inspection.message.condition.always.false"
                holder.registerProblem(expr, KotlinBundle.message(key))
            }
        }
    })
}