package com.minimart.pos.data.entity

import com.minimart.pos.util.Money

/** The sale's total as exact money, read from the whole-cent column. */
fun Sale.totalMoney(): Money = Money.fromCents(totalCents)

/** The cash physically taken for this sale (all of a cash sale, the cash share of a split sale), from whole cents. */
fun Sale.cashMoney(): Money = Money.fromCents(cashPortionCents)

/** Adds the totals of these sales in exact cents. */
fun Iterable<Sale>.totalOf(): Double = fold(Money.ZERO) { acc, s -> acc + s.totalMoney() }.toDouble()
