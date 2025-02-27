package org.jetbrains.kotlin.idea.inspections.dfa

import com.intellij.codeInspection.dataFlow.java.inst.BooleanBinaryInstruction
import com.intellij.codeInspection.dataFlow.java.inst.JvmPushInstruction
import com.intellij.codeInspection.dataFlow.java.inst.NumericBinaryInstruction
import com.intellij.codeInspection.dataFlow.lang.ir.*
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow.DeferredOffset
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeBinOp
import com.intellij.codeInspection.dataFlow.types.DfType
import com.intellij.codeInspection.dataFlow.types.DfTypes
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory
import com.intellij.codeInspection.dataFlow.value.RelationType
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

class KtControlFlowBuilder(val factory: DfaValueFactory, val context: KtExpression) {
    val flow = ControlFlow(factory, context)
    var broken: Boolean = false

    fun buildFlow(): ControlFlow? {
        processExpression(context)
        if (broken) return null
        addInstruction(PopInstruction()) // return value
        flow.finish()
        return flow
    }

    private fun processExpression(expr: KtExpression?) {
        when (expr) {
            null -> {
                pushUnknown()
            }
            is KtBlockExpression -> {
                val statements = expr.statements
                if (statements.isEmpty()) {
                    pushUnknown()
                } else {
                    for (child in statements) {
                        processExpression(child)
                        if (child != statements.last()) {
                            addInstruction(PopInstruction())
                        }
                        if (broken) return
                    }
                }
            }
            is KtBinaryExpression -> {
                processBinaryExpression(expr)
            }
            is KtConstantExpression -> {
                processConstantExpression(expr)
            }
            is KtSimpleNameExpression -> {
                processReferenceExpression(expr)
            }
            is KtIfExpression -> {
                processIfExpression(expr)
            }
            else -> {
                // unsupported construct
                broken = true
            }
        }
    }

    private fun processReferenceExpression(expr: KtSimpleNameExpression) {
        val target = expr.mainReference.resolve()
        if (target is KtCallableDeclaration) {
            if (target is KtParameter || target is KtProperty && target.isLocal) {
                addInstruction(
                    JvmPushInstruction(
                        KtLocalVariableDescriptor(target).createValue(factory, null),
                        KotlinExpressionAnchor(expr)
                    )
                )
                return
            }
        }
        // TODO: support other references
        broken = true
    }

    private fun processConstantExpression(expr: KtConstantExpression) {
        addInstruction(PushValueInstruction(getConstant(expr), KotlinExpressionAnchor(expr)))
    }

    private fun pushUnknown() {
        addInstruction(PushValueInstruction(DfType.TOP))
    }

    private fun processBinaryExpression(expr: KtBinaryExpression) {
        val token = expr.operationToken
        val relation = relationFromToken(token)
        if (relation != null) {
            processBinaryRelationExpression(expr, relation, token == KtTokens.EXCLEQ || token == KtTokens.EQEQ)
            return
        }
        val mathOp = mathOpFromToken(token)
        if (mathOp != null) {
            processMathExpression(expr, mathOp)
            return
        }
        if (token === KtTokens.ANDAND || token === KtTokens.OROR) {
            processShortCircuitExpression(expr, token === KtTokens.ANDAND)
            return
        }
        // TODO: support other operators
        broken = true
    }

    private fun processShortCircuitExpression(expr: KtBinaryExpression, and: Boolean) {
        val left = expr.left
        val right = expr.right
        val endOffset = DeferredOffset()
        processExpression(left)
        val nextOffset = DeferredOffset()
        addInstruction(ConditionalGotoInstruction(nextOffset, DfTypes.booleanValue(and), left))
        val anchor = KotlinExpressionAnchor(expr)
        addInstruction(PushValueInstruction(DfTypes.booleanValue(!and), anchor))
        addInstruction(GotoInstruction(endOffset))
        setOffset(nextOffset)
        addInstruction(FinishElementInstruction(null))
        processExpression(right)
        setOffset(endOffset)
        addInstruction(ResultOfInstruction(anchor))
    }

    private fun processMathExpression(expr: KtBinaryExpression, mathOp: LongRangeBinOp) {
        val left = expr.left
        val right = expr.right
        processExpression(left)
        processExpression(right)
        if (left == null || right == null) {
            addInstruction(EvalUnknownInstruction(KotlinExpressionAnchor(expr), 2))
            return
        }
        // TODO: support overloaded operators
        addInstruction(NumericBinaryInstruction(mathOp, KotlinExpressionAnchor(expr)))
    }

    private fun processBinaryRelationExpression(
        expr: KtBinaryExpression, relation: RelationType,
        forceEqualityByContent: Boolean
    ) {
        val left = expr.left
        val right = expr.right
        processExpression(left)
        processExpression(right)
        if (left == null || right == null) {
            addInstruction(EvalUnknownInstruction(KotlinExpressionAnchor(expr), 2))
            return
        }
        // TODO: support overloaded operators
        // TODO: avoid equals-comparison of unknown object types
        addInstruction(BooleanBinaryInstruction(relation, forceEqualityByContent, KotlinExpressionAnchor(expr)))
    }

    private fun addInstruction(inst: Instruction) {
        flow.addInstruction(inst)
    }

    private fun setOffset(offset: DeferredOffset) {
        offset.setOffset(flow.instructionCount)
    }

    private fun processIfExpression(ifExpression: KtIfExpression) {
        val condition = ifExpression.condition
        processExpression(condition)
        val skipThenOffset = DeferredOffset()
        val thenStatement = ifExpression.then
        val elseStatement = ifExpression.`else`
        addInstruction(ConditionalGotoInstruction(skipThenOffset, DfTypes.FALSE, condition))
        addInstruction(FinishElementInstruction(null))
        processExpression(thenStatement)

        val skipElseOffset = DeferredOffset()
        val instruction: Instruction = GotoInstruction(skipElseOffset)
        addInstruction(instruction)
        setOffset(skipThenOffset)
        addInstruction(FinishElementInstruction(null))
        processExpression(elseStatement)
        setOffset(skipElseOffset)
        addInstruction(FinishElementInstruction(ifExpression))
    }
}