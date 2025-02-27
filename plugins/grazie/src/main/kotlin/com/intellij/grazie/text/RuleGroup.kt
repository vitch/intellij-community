package com.intellij.grazie.text

import org.jetbrains.annotations.ApiStatus
import java.util.*

/** A user-defined set of rule ids or strings denoting abstract categories of rules. */
@ApiStatus.NonExtendable
open class RuleGroup(rules: Set<String>) {
  constructor(vararg rules: String) : this(rules.toSet())

  val rules: Set<String> = Collections.unmodifiableSet(rules)

  companion object {
    /** An abstract category for all rules that warn that sentences should be capitalized */
    const val SENTENCE_START_CASE = "UPPERCASE_SENTENCE_START"

    /**
     * An abstract category for all rules that warn that neutral sentences (neither questions nor exclamations)
     * should end with some punctuation (e.g. a dot)
     */
    const val SENTENCE_END_PUNCTUATION = "PUNCTUATION_PARAGRAPH_END"

    val EMPTY = RuleGroup()

    /** Rules for checking casing errors */
    val CASING = RuleGroup(SENTENCE_START_CASE)

    /** Rules for checking punctuation errors */
    val PUNCTUATION = RuleGroup(SENTENCE_END_PUNCTUATION, "UNLIKELY_OPENING_PUNCTUATION")

    /** Rules that are usually disabled for literal strings */
    val LITERALS = CASING + PUNCTUATION

    /**
     * Rules that allow for single sentences to be lowercase and lack starting/finishing punctuation,
     * useful in comments or commit messages
     */
    val UNDECORATED_SINGLE_SENTENCE = CASING + PUNCTUATION
  }

  operator fun plus(other: RuleGroup) = RuleGroup(rules + other.rules)

  override fun equals(other: Any?): Boolean = this === other || other is RuleGroup && rules == other.rules
  override fun hashCode(): Int = rules.hashCode()
  override fun toString(): String = rules.toString()
}