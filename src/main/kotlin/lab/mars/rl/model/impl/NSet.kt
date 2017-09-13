@file:Suppress("NOTHING_TO_INLINE", "UNCHECKED_CAST")

package lab.mars.rl.model.impl

import lab.mars.rl.model.*
import lab.mars.rl.util.ReadOnlyIntSlice
import lab.mars.rl.util.IntSlice
import java.util.NoSuchElementException

/**
 * <p>
 * Created on 2017-09-01.
 * </p>
 *
 * @author wumo
 */

/**
 * 将数组链表的最后dim.size个元素构成的数字依据dim进行进位加1
 *
 * 如数组链表为：0000123，`dim=[3,3,4]`，则进位加1仅影响最后`dim.size=3`个元素`123`，
 * `123++`的结果为`200`
 *
 * @receiver 表示index的数组链表
 */
fun IntSlice.increment(dim: IntArray) {
    val offset = lastIndex - dim.size + 1
    for (idx in dim.lastIndex downTo 0) {
        val this_idx = offset + idx
        this[this_idx]++
        if (this[this_idx] < dim[idx])
            break
        this[this_idx] = 0
    }
}

/**
 * 1. 可以定义任意维的多维数组，并使用`[]`进行取值赋值
 *如: `val a=Nset(2,3)`定义了一个2x3的矩阵，可以使用`a[0,0]=0`这样的用法
 *
 * 2. 可以嵌套使用[NSet]，定义不规则形状的树形结构
 * 如: `val a=Nset(2), a[0]=Nset(1), a[1]=Nset(2)`定义了一个不规则的结构，
 * 第一个元素长度为1第二个元素长度为2；同样可以通过`[]`来进行取值赋值，
 * 如`a[0,0]=0, a[1, 1]`，但`a[0,1]`和`a[1,2]`就会导致[ArrayIndexOutOfBoundsException]
 *
 * @param dim 根节点的维度定义
 * @param stride 各维度在一维数组上的跨度
 * @param root 根节点一维数组
 */
class NSet<E> private constructor(private val dim: IntArray, private val stride: IntArray, private val root: Array<Any?>) :
        IndexedCollection<E> {
    private var parent: NSet<E>? = null
    private fun refParent(s: Any?): Any? {
        val sub = s as? NSet<E>
        if (sub != null) sub.parent = this
        return s
    }

    private fun unrefParent(s: Any?): Any? {
        val sub = s as? NSet<E>
        if (sub != null) sub.parent = null
        return s
    }

    /**
     * 使用构造lambda [element_maker]来为数组的每个元素初始化
     * @param element_maker 提供了每个元素的index
     */
    override fun init(element_maker: (ReadOnlyIntSlice) -> Any?) {
        val index = IntSlice(dim.size)
        for (i in 0 until root.size) {
            val tmp = element_maker(index)
            root[i] = refParent(tmp).apply {
                index.increment(dim)
            }
        }
    }

    companion object {
        operator fun <T> invoke(vararg dim: Int, element_maker: (ReadOnlyIntSlice) -> Any? = { null }): NSet<T> {
            val stride = IntArray(dim.size)
            stride[stride.lastIndex] = 1
            for (a in stride.lastIndex - 1 downTo 0)
                stride[a] = dim[a + 1] * stride[a + 1]
            val total = dim[0] * stride[0]
            val raw = Array<Any?>(total) { null }
            return NSet<T>(dim, stride, raw).apply { init(element_maker) }
        }

        /**
         * 构造一个与[shape]相同形状的[NSet]（维度、树深度都相同）
         */
        operator fun <T> invoke(shape: NSet<*>, element_maker: (ReadOnlyIntSlice) -> Any? = { null }): NSet<T> {
            return NSet<T>(shape.dim, shape.stride, Array(shape.root.size) { null }).apply {
                val index = IntSlice(shape.dim.size)
                for (idx in 0 until this.root.size)
                    copycat(this, shape.root[idx], index, element_maker)
                            .apply { index.increment(shape.dim) }
            }
        }

        private fun <T> copycat(origin: NSet<T>, prototype: Any?, index: IntSlice, element_maker: (ReadOnlyIntSlice) -> Any?): Any? {
            return when (prototype) {
                is NSet<*> -> NSet<T>(prototype.dim, prototype.stride, Array(prototype.root.size) { null }).apply {
                    parent = origin
                    index.append(prototype.dim.size, 0)
                    for (idx in 0 until root.size)
                        copycat(this, prototype.root[idx], index, element_maker)
                                .apply { index.increment(prototype.dim) }
                    index.removeLast(prototype.dim.size)
                }
                else -> origin.refParent(element_maker(index))
            }
        }
    }

    private fun concat(indexable: Array<out Indexable>): IntArray {
        var total = 0
        indexable.forEach { total += it.idx.size }
        val idx = IntArray(total)
        var offset = 0
        indexable.forEach {
            System.arraycopy(it.idx, 0, idx, offset, it.idx.size)
            offset += it.idx.size
        }
        return idx
    }

    private fun <T> get_or_set(idx: IntArray, start: Int, set: Boolean, s: T?): T {
        var offset = 0
        val idx_size = idx.size - start
        if (idx_size < dim.size) throw RuntimeException("index.length=${idx.size - start}  < dim.length=${dim.size}")
        for (a in 0 until dim.size) {
            if (idx[start + a] < 0 || idx[start + a] > dim[a])
                throw ArrayIndexOutOfBoundsException("index[$a]= ${idx[start + a]} while dim[$a]=${dim[a]}")
            offset += idx[start + a] * stride[a]
        }
        return if (idx_size == dim.size) {
            val tmp = root[offset]
            if (set) {
                if (tmp != null) unrefParent(tmp)
                root[offset] = refParent(s)
            }
            tmp as T
        } else {
            val sub = root[offset] as? NSet<T> ?: throw RuntimeException("index dimension is larger than this set's element's dimension")
            sub.get_or_set(idx, start + dim.size, set, s)
        }
    }

    private inline fun _get(idx: IntArray): E = get_or_set<E>(idx, 0, false, null)

    private inline fun _set(idx: IntArray, s: E) = get_or_set(idx, 0, true, s)

    override operator fun get(vararg index: Int) = _get(index)

    override operator fun get(indexable: Indexable) = _get(indexable.idx)

    override operator fun get(vararg indexable: Indexable) = _get(concat(indexable))

    override operator fun set(vararg index: Int, s: E) {
        _set(index, s)
    }

    override operator fun set(indexable: Indexable, s: E) {
        _set(indexable.idx, s)
    }

    override operator fun set(vararg indexable: Indexable, s: E) {
        _set(concat(indexable), s)
    }

    operator fun set(vararg index: Int, s: NSet<E>) {
        require(index.size == dim.size) { "setting subset requires index.size == dim.size" }
        get_or_set(index, 0, true, s)
    }

    override fun iterator() = GeneralIterator<E>().apply { traverse = Traverse(this, {}, {}, {}, { it }) }

    fun indices() = GeneralIterator<ReadOnlyIntSlice>().apply {
        val index = IntSlice(this.set.dim.size).apply { this[lastIndex] = -1 }
        traverse = Traverse(this,
                            forward = {
                                index.apply {
                                    append(current.set.dim.size, 0)
                                    this[lastIndex] = -1
                                }
                            },
                            backward = { index.removeLast(current.set.dim.size) },
                            translate = { index.increment(current.set.dim) },
                            visitor = { index })
    }

    inner class GeneralIterator<T> : Iterator<T> {
        inner class Traverse(var current: GeneralIterator<T>,
                             val forward: Traverse.() -> Unit,
                             val backward: Traverse.() -> Unit,
                             val translate: Traverse.() -> Unit,
                             val visitor: Traverse.(E) -> T)

        internal lateinit var traverse: Traverse
        private var visited = -1
        internal val set = this@NSet
        private var parent: GeneralIterator<T> = this

        override fun hasNext(): Boolean {
            return dfs({ true }, { false })
        }

        override fun next(): T {
            return dfs({ traverse.current.increment();traverse.visitor(traverse, it) }, { throw NoSuchElementException() })
        }

        private inline fun increment(): Int {
            traverse.translate(traverse)
            return visited++
        }

        private inline fun <T> dfs(element: (E) -> T, nomore: () -> T): T {
            while (true) {
                while (traverse.current.visited + 1 < traverse.current.set.root.size) {
                    val tmp = traverse.current
                    val next = tmp.set.root[tmp.visited + 1]
                    tmp.inspect_next_type(next) ?: return element(next as E)
                }
                if (traverse.current === this) return nomore()
                traverse.backward(traverse)
                traverse.current = traverse.current.parent
            }
        }

        private inline fun inspect_next_type(next: Any?): NSet<E>? {
            val next_type = next as? NSet<E>
            next_type?.apply {
                val new = this.GeneralIterator<T>()
                new.traverse = traverse
                new.parent = traverse.current
                traverse.current = new
                new.parent.increment()
                traverse.forward(traverse)
            }
            return next_type
        }
    }
}

fun NSetMDP(gamma: Double, states: NSet<State?>, action_dim: (IntArray) -> IntArray) = MDP(
        states = states,
        gamma = gamma,
        v_maker = { NSet(states) { 0.0 } },
        q_maker = { NSet(states) { NSet<Double>(*action_dim(it.toIntArray())) { 0.0 } } },
        pi_maker = { NSet(states) })

fun NSetMDP(gamma: Double, state_dim: IntArray, action_dim: IntArray) = MDP(
        states = NSet(*state_dim) { State(it.toIntArray()) },
        gamma = gamma,
        v_maker = { NSet(*state_dim) { 0.0 } },
        q_maker = { NSet(*state_dim, *action_dim) { 0.0 } },
        pi_maker = { NSet(*state_dim) })

fun NSetMDP(gamma: Double, state_dim: IntArray, action_dim: (IntArray) -> IntArray) = MDP(
        states = NSet(*state_dim) { State(it.toIntArray()) },
        gamma = gamma,
        v_maker = { NSet(*state_dim) { 0.0 } },
        q_maker = { NSet(*state_dim) { NSet<Double>(*action_dim(it.toIntArray())) { 0.0 } } },
        pi_maker = { NSet(*state_dim) })