package com.invent.ontocook.sequence

abstract class SequenceAdapter<T> where T : Any {

    abstract fun getCount(): Int

    abstract fun getItem(position: Int): T

    abstract fun bindView(sequenceStep: SequenceStep, item: T)
}
